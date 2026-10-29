package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

class PointsResolverTest {

    private static Prediction prediction(Integer home, Integer away, Integer points) {
        return Prediction.builder().homeTeamScore(home).awayTeamScore(away).points(points).build();
    }

    private static Match match(String status, Integer home, Integer away) {
        return Match.builder().status(status).homeTeamScore(home).awayTeamScore(away).build();
    }

    @Test
    void notStartedMatchHasNoPoints() {
        assertNull(PointsResolver.reviewPoints(match("ns", null, null), prediction(1, 0, null)));
        assertNull(PointsResolver.reviewPoints(match("ns", 0, 0), prediction(1, 0, null)));
    }

    @Test
    void finishedMatchUsesStoredPointsWhenPresent() {
        assertEquals(7, PointsResolver.reviewPoints(match("ft", 2, 1), prediction(2, 1, 7)));
    }

    @Test
    void finishedMatchWithoutStoredPointsIsComputed() {
        Prediction p = prediction(2, 1, null);
        assertEquals(PredictionService.computePoints(2, 1, 2, 1), PointsResolver.reviewPoints(match("ft", 2, 1), p));
    }

    @Test
    void liveMatchIgnoresStoredPointsAndIsProvisional() {
        Prediction p = prediction(1, 0, 99);
        assertEquals(PredictionService.computePoints(1, 0, 1, 0), PointsResolver.reviewPoints(match("1h", 1, 0), p));
    }

    @Test
    void missingPredictionPenalisedInEplButNotInCup() {
        assertEquals(-1, PointsResolver.reviewPoints(match("ft", 1, 0), null));
        CupMatch cup = CupMatch.builder().status("ft").homeTeamScore(1).awayTeamScore(0).build();
        assertEquals(0, PointsResolver.cupReviewPoints(cup, null));
    }

    @Test
    void liveWithoutScoreHasNoPoints() {
        assertNull(PointsResolver.resolve("1h", null, null, prediction(1, 0, null), ScoringMode.EPL));
    }
}
