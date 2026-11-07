package zhigalin.predictions.miniapp.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.MiniAppService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionDeleteRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionListItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionUpsertRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminRowMutationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;
import zhigalin.predictions.service.user.UserService;

@Service
public class AdminPredictionService {

    private final AdminGuard adminGuard;
    private final PredictionService predictionService;
    private final PredictionDao predictionDao;
    private final MatchService matchService;
    private final CupMatchDao cupMatchDao;
    private final MiniAppService miniAppService;
    private final UserService userService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;

    public AdminPredictionService(
            AdminGuard adminGuard,
            PredictionService predictionService,
            PredictionDao predictionDao,
            MatchService matchService,
            CupMatchDao cupMatchDao,
            MiniAppService miniAppService,
            UserService userService,
            UserWeekBonusMatchService userWeekBonusMatchService
    ) {
        this.adminGuard = adminGuard;
        this.predictionService = predictionService;
        this.predictionDao = predictionDao;
        this.matchService = matchService;
        this.cupMatchDao = cupMatchDao;
        this.miniAppService = miniAppService;
        this.userService = userService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
    }

    public List<AdminPredictionListItem> list(
            String telegramId,
            String scope,
            Integer weekId,
            Integer userId
    ) {
        adminGuard.requireAdmin(telegramId);
        String normalized = scope == null ? "" : scope.trim().toLowerCase();
        List<AdminPredictionListItem> rows = new ArrayList<>();
        Map<Integer, User> usersById = userService.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        if ("week".equals(normalized)) {
            if (weekId == null || weekId <= 0) {
                throw new MiniAppException(400, "Нужен weekId для scope=week.");
            }
            for (Match match : matchService.findAllByWeekId(weekId)) {
                MatchItem card = miniAppService.adminMatchCard(match);
                for (Prediction p : predictionService.getByMatchPublicId(match.getId())) {
                    if (userId != null && userId > 0 && p.getUserId() != userId) {
                        continue;
                    }
                    rows.add(toListItem("epl", p, usersById.get(p.getUserId()), card, match.getStatus(), weekId));
                }
            }
        } else if ("cups".equals(normalized)) {
            for (MatchItem card : miniAppService.adminCupMatchesToday()) {
                CupMatch cup = cupMatchDao.findById(card.publicId());
                String status = cup != null ? cup.getStatus() : null;
                for (Prediction p : predictionService.getByCupMatchPublicId(card.publicId())) {
                    if (userId != null && userId > 0 && p.getUserId() != userId) {
                        continue;
                    }
                    rows.add(toListItem("cup", p, usersById.get(p.getUserId()), card, status, null));
                }
            }
        } else {
            throw new MiniAppException(400, "scope: week или cups.");
        }
        rows.sort(Comparator
                .comparing(AdminPredictionListItem::homeCode, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AdminPredictionListItem::awayCode, String.CASE_INSENSITIVE_ORDER)
                .thenComparing((AdminPredictionListItem r) -> r.login(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private AdminPredictionListItem toListItem(
            String kind,
            Prediction p,
            User u,
            MatchItem card,
            String matchStatus,
            Integer weekId
    ) {
        boolean weekBonus = false;
        if ("epl".equals(kind) && weekId != null && u != null) {
            weekBonus = userWeekBonusMatchService.findAssigned(u.getId(), weekId)
                    .map(id -> id == p.getMatchId())
                    .orElse(false);
        }
        Integer matchId = "epl".equals(kind) ? p.getMatchId() : null;
        Integer cupMatchId = "cup".equals(kind) ? p.getCupMatchId() : null;
        return new AdminPredictionListItem(
                kind,
                p.getUserId(),
                u != null ? u.getLogin() : ("user#" + p.getUserId()),
                u != null ? u.getTelegramId() : null,
                u != null ? u.getRole() : null,
                matchId,
                cupMatchId,
                p.getHomeScore(),
                p.getAwayScore(),
                p.getPoints(),
                weekBonus,
                matchStatus,
                card.homeCode() != null ? card.homeCode() : "",
                card.awayCode() != null ? card.awayCode() : "",
                card.homeName() != null ? card.homeName() : "",
                card.awayName() != null ? card.awayName() : "",
                card.homeLogo() != null ? card.homeLogo() : "",
                card.awayLogo() != null ? card.awayLogo() : "",
                weekId
        );
    }

    @Transactional
    public AdminRowMutationResponse upsert(String telegramId, AdminPredictionUpsertRequest body) {
        adminGuard.requireAdmin(telegramId);
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean hasMatch = body.matchId() != null && body.matchId() > 0;
        boolean hasCup = body.cupMatchId() != null && body.cupMatchId() > 0;
        if (hasMatch == hasCup) {
            throw new MiniAppException(400, "Укажите ровно одно: matchId или cupMatchId.");
        }
        if (body.userId() <= 0) {
            throw new MiniAppException(400, "Нужен userId.");
        }
        Prediction prediction = Prediction.builder()
                .userId(body.userId())
                .homeScore(body.homeScore())
                .awayScore(body.awayScore())
                .build();
        if (hasCup) {
            prediction.setCupMatchId(body.cupMatchId());
            prediction.setMatchId(0);
            predictionService.save(prediction);
            refreshCupPointsIfFinished(body.cupMatchId(), body.userId());
        } else {
            prediction.setMatchId(body.matchId());
            predictionService.save(prediction);
            refreshEplPointsIfFinished(body.matchId(), body.userId());
        }
        return new AdminRowMutationResponse(true, "Прогноз сохранён");
    }

    @Transactional
    public AdminRowMutationResponse delete(String telegramId, AdminPredictionDeleteRequest body) {
        adminGuard.requireAdmin(telegramId);
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean hasMatch = body.matchId() != null && body.matchId() > 0;
        boolean hasCup = body.cupMatchId() != null && body.cupMatchId() > 0;
        if (hasMatch == hasCup) {
            throw new MiniAppException(400, "Укажите ровно одно: matchId или cupMatchId.");
        }
        if (hasCup) {
            predictionDao.deleteCup(body.userId(), body.cupMatchId());
        } else {
            predictionDao.delete(body.userId(), body.matchId());
        }
        return new AdminRowMutationResponse(true, "Прогноз удалён");
    }

    private void refreshEplPointsIfFinished(int matchId, int userId) {
        Match match = matchService.findById(matchId);
        if (match == null || !"ft".equals(match.getStatus())) {
            return;
        }
        predictionService.updatePoints(matchId, userId);
    }

    private void refreshCupPointsIfFinished(int cupMatchId, int userId) {
        CupMatch cup = cupMatchDao.findById(cupMatchId);
        if (cup == null || !"ft".equals(cup.getStatus())) {
            return;
        }
        Prediction prediction = predictionDao.findByCupMatchIdAndUserId(cupMatchId, userId);
        if (prediction == null) {
            return;
        }
        int points = PredictionService.computePoints(
                ScoringMode.CUP,
                cup.getHomeScore(),
                cup.getAwayScore(),
                prediction.getHomeScore(),
                prediction.getAwayScore()
        );
        predictionDao.updateCupPointsBatch(List.of(new PredictionDao.CupPointsUpdate(cupMatchId, userId, points)));
    }
}
