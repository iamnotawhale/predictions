package zhigalin.predictions.miniapp.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.MiniAppService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminCupMatchDetailResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminMatchListItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminMatchPatchRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminRowMutationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.AppTimeZones;

@Service
public class AdminCupMatchService {

    private final AdminGuard adminGuard;
    private final AdminEspnSyncGuard espnSyncGuard;
    private final MiniAppService miniAppService;
    private final CupMatchDao cupMatchDao;
    private final PredictionService predictionService;
    private final UserService userService;

    public AdminCupMatchService(
            AdminGuard adminGuard,
            AdminEspnSyncGuard espnSyncGuard,
            MiniAppService miniAppService,
            CupMatchDao cupMatchDao,
            PredictionService predictionService,
            UserService userService
    ) {
        this.adminGuard = adminGuard;
        this.espnSyncGuard = espnSyncGuard;
        this.miniAppService = miniAppService;
        this.cupMatchDao = cupMatchDao;
        this.predictionService = predictionService;
        this.userService = userService;
    }

    public List<AdminMatchListItem> listToday(String telegramId) {
        adminGuard.requireAdmin(telegramId);
        return miniAppService.adminCupMatchesToday().stream()
                .map(this::toListItemFromCard)
                .toList();
    }

    public AdminCupMatchDetailResponse getMatch(String telegramId, int publicId) {
        adminGuard.requireAdmin(telegramId);
        CupMatch cup = cupMatchDao.findById(publicId);
        if (cup == null) {
            throw new MiniAppException(404, "Кубковый матч не найден.");
        }
        return toDetail(cup);
    }

    @Transactional
    public AdminRowMutationResponse patchMatch(String telegramId, int publicId, AdminMatchPatchRequest body) {
        adminGuard.requireAdmin(telegramId);
        CupMatch cup = cupMatchDao.findById(publicId);
        if (cup == null) {
            throw new MiniAppException(404, "Кубковый матч не найден.");
        }
        if (espnSyncGuard.isCupMatchEditBlocked(cup)) {
            throw new MiniAppException(
                    409,
                    "ESPN-sync ещё перезапишет этот кубковый матч (сегодня + активный poll). Подождите."
            );
        }
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean scoreOrStatusChanged = false;
        String prevStatus = cup.getStatus();
        if (body.homeScore() != null) {
            cup.setHomeScore(body.homeScore());
            scoreOrStatusChanged = true;
        }
        if (body.awayScore() != null) {
            cup.setAwayScore(body.awayScore());
            scoreOrStatusChanged = true;
        }
        if (body.status() != null && !body.status().isBlank()) {
            cup.setStatus(body.status().trim());
            scoreOrStatusChanged = true;
        }
        if (body.kickoffLocal() != null && !body.kickoffLocal().isBlank()) {
            cup.setStartsAt(parseKickoffLocal(body.kickoffLocal()));
        }
        if ("ft".equals(cup.getStatus()) && !"ft".equals(prevStatus) && cup.getFinishedAt() == null) {
            cup.setFinishedAt(LocalDateTime.now(AppTimeZones.DISPLAY));
        }
        cupMatchDao.upsert(cup);
        int inserted = 0;
        if (shouldRecalc(cup, scoreOrStatusChanged)) {
            int before = predictionService.getByCupMatchPublicId(publicId).size();
            predictionService.updateByCupMatch(cup);
            int after = predictionService.getByCupMatchPublicId(publicId).size();
            inserted = Math.max(0, after - before);
        }
        String msg = inserted > 0
                ? "Матч обновлён. Пересчитаны очки; добавлено прогнозов: " + inserted + "."
                : "Матч обновлён. Очки пересчитаны.";
        if (!shouldRecalc(cup, scoreOrStatusChanged)) {
            msg = "Матч обновлён.";
        }
        return new AdminRowMutationResponse(true, msg);
    }

    private AdminMatchListItem toListItemFromCard(MatchItem card) {
        CupMatch cup = cupMatchDao.findById(card.publicId());
        return new AdminMatchListItem(
                card,
                cup != null && espnSyncGuard.isCupMatchEditBlocked(cup),
                cup != null ? formatKickoffLocal(cup.getStartsAt()) : ""
        );
    }

    private AdminCupMatchDetailResponse toDetail(CupMatch cup) {
        List<Prediction> predictions = predictionService.getByCupMatchPublicId(cup.getId());
        Map<Integer, User> usersById = userService.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<AdminPredictionItem> rows = new ArrayList<>();
        for (Prediction p : predictions) {
            User u = usersById.get(p.getUserId());
            rows.add(new AdminPredictionItem(
                    p.getUserId(),
                    u != null ? u.getLogin() : ("user#" + p.getUserId()),
                    u != null ? u.getTelegramId() : null,
                    u != null ? u.getRole() : null,
                    p.getHomeScore(),
                    p.getAwayScore(),
                    p.getPoints(),
                    false
            ));
        }
        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.login(), b.login()));
        int usersTotal = usersById.size();
        int predictionsCount = predictions.size();
        int missingOnRecalc = 0;
        if (!predictions.isEmpty() && predictionsCount < usersTotal) {
            missingOnRecalc = usersTotal - predictionsCount;
        }
        MatchItem card = miniAppService.adminCupCard(cup);
        return new AdminCupMatchDetailResponse(
                card,
                espnSyncGuard.isCupMatchEditBlocked(cup),
                formatKickoffLocal(cup.getStartsAt()),
                rows,
                usersTotal,
                predictionsCount,
                missingOnRecalc,
                cup.getAetHome(),
                cup.getAetAway(),
                cup.getPenHome(),
                cup.getPenAway()
        );
    }

    private static boolean shouldRecalc(CupMatch cup, boolean scoreOrStatusChanged) {
        return scoreOrStatusChanged
                && "ft".equals(cup.getStatus())
                && cup.getHomeScore() != null
                && cup.getAwayScore() != null;
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
            throw new MiniAppException(400, "Неверный формат kickoffLocal.");
        }
    }
}
