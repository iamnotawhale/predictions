package zhigalin.predictions.miniapp.admin;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionDeleteRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminPredictionUpsertRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminRowMutationResponse;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

@Service
public class AdminPredictionService {

    private final AdminGuard adminGuard;
    private final PredictionService predictionService;
    private final PredictionDao predictionDao;
    private final MatchService matchService;
    private final CupMatchDao cupMatchDao;

    public AdminPredictionService(
            AdminGuard adminGuard,
            PredictionService predictionService,
            PredictionDao predictionDao,
            MatchService matchService,
            CupMatchDao cupMatchDao
    ) {
        this.adminGuard = adminGuard;
        this.predictionService = predictionService;
        this.predictionDao = predictionDao;
        this.matchService = matchService;
        this.cupMatchDao = cupMatchDao;
    }

    @Transactional
    public AdminRowMutationResponse upsert(String telegramId, AdminPredictionUpsertRequest body) {
        adminGuard.requireAdmin(telegramId);
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean hasMatch = body.matchPublicId() != null && body.matchPublicId() > 0;
        boolean hasCup = body.cupMatchId() != null && body.cupMatchId() > 0;
        if (hasMatch == hasCup) {
            throw new MiniAppException(400, "Укажите ровно одно: matchPublicId или cupMatchId.");
        }
        if (body.userId() <= 0) {
            throw new MiniAppException(400, "Нужен userId.");
        }
        Prediction prediction = Prediction.builder()
                .userId(body.userId())
                .homeTeamScore(body.homeScore())
                .awayTeamScore(body.awayScore())
                .build();
        if (hasCup) {
            prediction.setCupMatchId(body.cupMatchId());
            prediction.setMatchPublicId(0);
            predictionService.save(prediction);
            refreshCupPointsIfFinished(body.cupMatchId(), body.userId());
        } else {
            prediction.setMatchPublicId(body.matchPublicId());
            predictionService.save(prediction);
            refreshEplPointsIfFinished(body.matchPublicId(), body.userId());
        }
        return new AdminRowMutationResponse(true, "Прогноз сохранён");
    }

    @Transactional
    public AdminRowMutationResponse delete(String telegramId, AdminPredictionDeleteRequest body) {
        adminGuard.requireAdmin(telegramId);
        if (body == null) {
            throw new MiniAppException(400, "Пустой запрос.");
        }
        boolean hasMatch = body.matchPublicId() != null && body.matchPublicId() > 0;
        boolean hasCup = body.cupMatchId() != null && body.cupMatchId() > 0;
        if (hasMatch == hasCup) {
            throw new MiniAppException(400, "Укажите ровно одно: matchPublicId или cupMatchId.");
        }
        if (hasCup) {
            predictionDao.deleteCup(body.userId(), body.cupMatchId());
        } else {
            predictionDao.delete(body.userId(), body.matchPublicId());
        }
        return new AdminRowMutationResponse(true, "Прогноз удалён");
    }

    private void refreshEplPointsIfFinished(int matchPublicId, int userId) {
        Match match = matchService.findByPublicId(matchPublicId);
        if (match == null || !"ft".equals(match.getStatus())) {
            return;
        }
        predictionService.updatePoints(matchPublicId, userId);
    }

    private void refreshCupPointsIfFinished(int cupMatchId, int userId) {
        CupMatch cup = cupMatchDao.findByPublicId(cupMatchId);
        if (cup == null || !"ft".equals(cup.getStatus())) {
            return;
        }
        Prediction prediction = predictionDao.findByCupMatchIdAndUserId(cupMatchId, userId);
        if (prediction == null) {
            return;
        }
        int points = PredictionService.computePoints(
                ScoringMode.CUP,
                cup.getHomeTeamScore(),
                cup.getAwayTeamScore(),
                prediction.getHomeTeamScore(),
                prediction.getAwayTeamScore()
        );
        predictionDao.updateCupPointsBatch(List.of(new PredictionDao.CupPointsUpdate(cupMatchId, userId, points)));
    }
}
