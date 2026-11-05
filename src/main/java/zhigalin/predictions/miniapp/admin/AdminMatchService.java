package zhigalin.predictions.miniapp.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.MiniAppService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminMatchDetailResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminMatchListItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminMatchPatchRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminRowMutationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.AppTimeZones;

@Service
public class AdminMatchService {

    private final AdminGuard adminGuard;
    private final AdminEspnSyncGuard espnSyncGuard;
    private final MiniAppService miniAppService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final UserService userService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;

    public AdminMatchService(
            AdminGuard adminGuard,
            AdminEspnSyncGuard espnSyncGuard,
            MiniAppService miniAppService,
            MatchService matchService,
            PredictionService predictionService,
            UserService userService,
            UserWeekBonusMatchService userWeekBonusMatchService
    ) {
        this.adminGuard = adminGuard;
        this.espnSyncGuard = espnSyncGuard;
        this.miniAppService = miniAppService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.userService = userService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
    }

    public List<AdminMatchListItem> listMatches(String telegramId, int weekId) {
        adminGuard.requireAdmin(telegramId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        return matches.stream()
                .map(m -> toListItem(m))
                .toList();
    }

    public AdminMatchDetailResponse getMatch(String telegramId, int publicId) {
        adminGuard.requireAdmin(telegramId);
        Match match = matchService.findByPublicId(publicId);
        if (match == null) {
            throw new MiniAppException(404, "Матч не найден.");
        }
        return toDetail(match);
    }

    @Transactional
    public AdminRowMutationResponse patchMatch(String telegramId, int publicId, AdminMatchPatchRequest body) {
        adminGuard.requireAdmin(telegramId);
        Match match = matchService.findByPublicId(publicId);
        if (match == null) {
            throw new MiniAppException(404, "Матч не найден.");
        }
        if (espnSyncGuard.isEplMatchEditBlocked(match)) {
            throw new MiniAppException(
                    409,
                    "ESPN-sync ещё перезапишет этот матч (live / перенос / недавний FT). Подождите или правьте старый FT вне scoreboard."
            );
        }
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean scoreOrStatusChanged = false;
        String prevStatus = match.getStatus();
        if (body.homeScore() != null) {
            match.setHomeTeamScore(body.homeScore());
            scoreOrStatusChanged = true;
        }
        if (body.awayScore() != null) {
            match.setAwayTeamScore(body.awayScore());
            scoreOrStatusChanged = true;
        }
        if (body.status() != null && !body.status().isBlank()) {
            match.setStatus(body.status().trim());
            scoreOrStatusChanged = true;
        }
        if (body.kickoffLocal() != null && !body.kickoffLocal().isBlank()) {
            match.setLocalDateTime(parseKickoffLocal(body.kickoffLocal()));
        }
        if (scoreOrStatusChanged
                && match.getHomeTeamScore() != null
                && match.getAwayTeamScore() != null) {
            match.setResult(findResult(match.getHomeTeamScore(), match.getAwayTeamScore()));
        }
        if ("ft".equals(match.getStatus()) && !"ft".equals(prevStatus) && match.getFinishedAt() == null) {
            match.setFinishedAt(LocalDateTime.now(AppTimeZones.DISPLAY));
        }
        matchService.update(match);
        int inserted = 0;
        if (shouldRecalc(match, scoreOrStatusChanged)) {
            int before = predictionService.getByMatchPublicId(publicId).size();
            predictionService.updateByMatch(match);
            int after = predictionService.getByMatchPublicId(publicId).size();
            inserted = Math.max(0, after - before);
        }
        String msg = inserted > 0
                ? "Матч обновлён. Пересчитаны очки; добавлено прогнозов: " + inserted + "."
                : "Матч обновлён. Очки пересчитаны.";
        if (!shouldRecalc(match, scoreOrStatusChanged)) {
            msg = "Матч обновлён.";
        }
        return new AdminRowMutationResponse(true, msg);
    }

    private boolean shouldRecalc(Match match, boolean scoreOrStatusChanged) {
        return scoreOrStatusChanged
                && "ft".equals(match.getStatus())
                && match.getHomeTeamScore() != null
                && match.getAwayTeamScore() != null;
    }

    private AdminMatchListItem toListItem(Match match) {
        MatchItem card = miniAppService.adminMatchCard(match);
        return new AdminMatchListItem(
                card,
                espnSyncGuard.isEplMatchEditBlocked(match),
                formatKickoffLocal(match.getLocalDateTime())
        );
    }

    private AdminMatchDetailResponse toDetail(Match match) {
        List<Prediction> predictions = predictionService.getByMatchPublicId(match.getPublicId());
        Map<Integer, User> usersById = userService.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<AdminPredictionItem> rows = new ArrayList<>();
        for (Prediction p : predictions) {
            User u = usersById.get(p.getUserId());
            boolean weekBonus = userWeekBonusMatchService.findAssigned(p.getUserId(), match.getWeekId())
                    .map(id -> id == match.getPublicId())
                    .orElse(false);
            rows.add(new AdminPredictionItem(
                    p.getUserId(),
                    u != null ? u.getLogin() : ("user#" + p.getUserId()),
                    p.getHomeTeamScore(),
                    p.getAwayTeamScore(),
                    p.getPoints(),
                    weekBonus
            ));
        }
        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.login(), b.login()));
        int usersTotal = usersById.size();
        int predictionsCount = predictions.size();
        int missingOnRecalc = 0;
        if (!predictions.isEmpty() && predictionsCount < usersTotal) {
            missingOnRecalc = usersTotal - predictionsCount;
        }
        MatchItem card = miniAppService.adminMatchCard(match);
        return new AdminMatchDetailResponse(
                card,
                espnSyncGuard.isEplMatchEditBlocked(match),
                formatKickoffLocal(match.getLocalDateTime()),
                rows,
                usersTotal,
                predictionsCount,
                missingOnRecalc
        );
    }

    private static String formatKickoffLocal(LocalDateTime ldt) {
        if (ldt == null) {
            return "";
        }
        return ldt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static LocalDateTime parseKickoffLocal(String raw) {
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new MiniAppException(400, "Неверный формат kickoffLocal (ожидается ISO local date-time).");
        }
    }

    private static String findResult(Integer homeScore, Integer awayScore) {
        if (homeScore.equals(awayScore)) {
            return "D";
        }
        return homeScore > awayScore ? "H" : "A";
    }
}
