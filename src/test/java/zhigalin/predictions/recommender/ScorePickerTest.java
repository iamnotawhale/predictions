package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ScorePickerTest {

    private static final int N = 6;

    private static double[][] point(int h, int a) {
        double[][] m = new double[N][N];
        m[h][a] = 1.0;
        return m;
    }

    private static double[][] poisson(double lh, double la) {
        double[][] m = new double[N][N];
        double sum = 0;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                m[i][j] = pmf(i, lh) * pmf(j, la);
                sum += m[i][j];
            }
        }
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                m[i][j] /= sum;
            }
        }
        return m;
    }

    private static double pmf(int k, double l) {
        double f = 1;
        for (int i = 2; i <= k; i++) {
            f *= i;
        }
        return Math.exp(-l) * Math.pow(l, k) / f;
    }

    @Test
    void certainScoreIsPickedAndWorthFourPoints() {
        ScorePicker.Pick pick = ScorePicker.bestByExpectedPoints(point(2, 1));
        assertEquals(2, pick.home());
        assertEquals(1, pick.away());
        assertEquals(4.0, pick.expectedPoints(), 1e-9);
        assertEquals(1.0, pick.probability(), 1e-9);
    }

    @Test
    void expectedPointsFollowThePayoffTable() {
        // truth is always 2:0. Predict 2:0 -> 4, 3:1 (same diff) -> 2, 1:0 (same outcome) -> 1, 0:1 -> -1
        double[][] m = point(2, 0);
        assertEquals(4, ScorePicker.expectedPoints(m, 2, 0), 1e-9);
        assertEquals(2, ScorePicker.expectedPoints(m, 3, 1), 1e-9);
        assertEquals(1, ScorePicker.expectedPoints(m, 1, 0), 1e-9);
        assertEquals(-1, ScorePicker.expectedPoints(m, 0, 1), 1e-9);
    }

    @Test
    void anyDrawPaysTwoWhenTheRealScoreIsAnotherDraw() {
        double[][] m = point(0, 0);
        assertEquals(2, ScorePicker.expectedPoints(m, 1, 1), 1e-9);
        assertEquals(2, ScorePicker.expectedPoints(m, 2, 2), 1e-9);
    }

    @Test
    void evenMatchPrefersASafeWinnerOverTheModalDraw() {
        // near-even game: the single most likely score is 1:1, but 1:0 pays more on average
        double[][] m = poisson(1.35, 1.2);
        assertEquals(1, ScorePicker.mostLikely(m).home());
        assertEquals(1, ScorePicker.mostLikely(m).away());
        ScorePicker.Pick pick = ScorePicker.bestByExpectedPoints(m);
        assertTrue(pick.home() != pick.away(), "should not recommend a draw: " + pick);
        assertTrue(pick.expectedPoints() > ScorePicker.mostLikely(m).expectedPoints());
    }

    @Test
    void neverWorseThanTheMostLikelyScore() {
        Random rnd = new Random(42);
        for (int t = 0; t < 300; t++) {
            double[][] m = poisson(0.6 + rnd.nextDouble() * 2.6, 0.5 + rnd.nextDouble() * 2.4);
            ScorePicker.Pick best = ScorePicker.bestByExpectedPoints(m);
            ScorePicker.Pick modal = ScorePicker.mostLikely(m);
            assertTrue(best.expectedPoints() >= modal.expectedPoints() - 1e-9, "t=" + t);
        }
    }

    @Test
    void pickIsAGlobalMaximumOfExpectedPoints() {
        double[][] m = poisson(1.9, 0.9);
        ScorePicker.Pick best = ScorePicker.bestByExpectedPoints(m);
        for (int h = 0; h < N; h++) {
            for (int a = 0; a < N; a++) {
                assertTrue(ScorePicker.expectedPoints(m, h, a) <= best.expectedPoints() + 1e-9);
            }
        }
    }

    @Test
    void tiesGoToTheLowerScore() {
        // symmetric matrix of all zeros -> every candidate pays 0 (no mass), first (0:0) wins
        ScorePicker.Pick pick = ScorePicker.bestByExpectedPoints(new double[N][N]);
        assertEquals(0, pick.home());
        assertEquals(0, pick.away());
    }
}
