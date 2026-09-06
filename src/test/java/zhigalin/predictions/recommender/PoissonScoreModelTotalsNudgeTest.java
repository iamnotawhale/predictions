package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PoissonScoreModelTotalsNudgeTest {

    @Test
    void matchTotalNudgePullsSumTowardLine() {
        double[] nudged = PoissonScoreModel.applyMatchTotalNudge(2.0, 1.0, 2.5);
        double expected = 3.0 * 0.70 + 2.5 * 0.30;
        assertEquals(expected, nudged[0] + nudged[1], 1e-9);
        assertEquals(2.0 / 3.0, nudged[0] / (nudged[0] + nudged[1]), 1e-9);
    }

    @Test
    void matchTotalNudgeIgnoredWhenMissing() {
        double[] nudged = PoissonScoreModel.applyMatchTotalNudge(1.4, 1.2, null);
        assertEquals(1.4, nudged[0]);
        assertEquals(1.2, nudged[1]);
    }

    @Test
    void teamTotalNudgeBlendsTowardLine() {
        double nudged = PoissonScoreModel.applyTeamTotalNudge(1.0, 1.5);
        assertEquals(1.125, nudged, 1e-9);
        assertEquals(1.0, PoissonScoreModel.applyTeamTotalNudge(1.0, null));
    }
}
