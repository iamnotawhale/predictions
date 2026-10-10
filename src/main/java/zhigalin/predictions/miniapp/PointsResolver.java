package zhigalin.predictions.miniapp;

import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

/** Points shown in week / cup review: stored once finished, provisional while in play, null otherwise. */
final class PointsResolver {
    private PointsResolver() {
    }

    static Integer reviewPoints(Match match, Prediction prediction) {
        return resolve(match.getStatus(), match.getHomeTeamScore(), match.getAwayTeamScore(),
                prediction, ScoringMode.EPL);
    }

    static Integer cupReviewPoints(BonusMatch match, Prediction prediction) {
        return resolve(match.getStatus(), match.getHomeTeamScore(), match.getAwayTeamScore(),
                prediction, ScoringMode.CUP);
    }

    static Integer resolve(
            String status, Integer homeScore, Integer awayScore, Prediction prediction, ScoringMode mode
    ) {
        boolean finished = MatchStatus.isFinished(status);
        if (!finished && !MatchStatus.isLiveLike(status, homeScore, awayScore)) {
            return null;
        }
        if (finished && prediction != null && prediction.getPoints() != null) {
            return prediction.getPoints();
        }
        if (homeScore == null || awayScore == null) {
            return null;
        }
        return PredictionService.computePoints(
                mode,
                homeScore,
                awayScore,
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null
        );
    }
}
