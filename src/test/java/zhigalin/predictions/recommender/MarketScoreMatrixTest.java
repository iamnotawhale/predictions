package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketScoreMatrixTest {

    private static double[] outcomes(double[][] m) {
        double h = 0;
        double d = 0;
        double a = 0;
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m.length; j++) {
                if (i > j) {
                    h += m[i][j];
                } else if (i == j) {
                    d += m[i][j];
                } else {
                    a += m[i][j];
                }
            }
        }
        return new double[] {h, d, a};
    }

    private static double sum(double[][] m) {
        double s = 0;
        for (double[] row : m) {
            for (double v : row) {
                s += v;
            }
        }
        return s;
    }

    @Test
    void poissonMatrixIsNormalized() {
        double[][] m = MarketScoreMatrix.poisson(1.6, 1.1, 5);
        assertEquals(6, m.length);
        assertEquals(1.0, sum(m), 1e-12);
        assertTrue(m[1][1] > m[4][4]);
    }

    @Test
    void calibrationReproducesTheMarketExactlyAndKeepsTheShapeInsideEachOutcome() {
        double[][] raw = MarketScoreMatrix.poisson(1.4, 1.2, 5);
        PoissonScoreModel.MarketOutcome market = new PoissonScoreModel.MarketOutcome(0.50, 0.30, 0.20);
        double[][] cal = MarketScoreMatrix.calibrateTo1x2(raw, market);
        double[] o = outcomes(cal);
        assertEquals(0.50, o[0], 1e-6);
        assertEquals(0.30, o[1], 1e-6);
        assertEquals(0.20, o[2], 1e-6);
        assertEquals(1.0, sum(cal), 1e-9);
        // two home-win cells keep their ratio (only whole regions are rescaled)
        assertEquals(raw[2][0] / raw[1][0], cal[2][0] / cal[1][0], 1e-9);
        // input untouched
        assertEquals(1.0, sum(raw), 1e-12);
    }

    @Test
    void fitRecoversRatesFromTheirOwnOutcomeProbabilities() {
        double[][] m = MarketScoreMatrix.poisson(1.7, 1.1, 10);
        double[] o = outcomes(m);
        MarketScoreMatrix.Lambdas fit = MarketScoreMatrix.fit(
                new PoissonScoreModel.MarketOutcome(o[0], o[1], o[2]), 2.8);
        assertEquals(1.7, fit.home(), 0.12);
        assertEquals(1.1, fit.away(), 0.12);
        assertTrue(fit.home() > fit.away());
    }

    @Test
    void totalTargetMovesTheFittedRatesButNotTheirBalance() {
        PoissonScoreModel.MarketOutcome even = new PoissonScoreModel.MarketOutcome(0.36, 0.28, 0.36);
        MarketScoreMatrix.Lambdas low = MarketScoreMatrix.fit(even, 2.2);
        MarketScoreMatrix.Lambdas high = MarketScoreMatrix.fit(even, 3.6);
        assertTrue(high.home() + high.away() > low.home() + low.away() + 0.5);
        assertEquals(high.home(), high.away(), 0.1);
    }

    @Test
    void totalMeanForOverMatchesThePoissonTail() {
        double mu = MarketScoreMatrix.totalMeanForOver(2.5, 0.566);
        double pOver = 1 - Math.exp(-mu) * (1 + mu + mu * mu / 2);
        assertEquals(0.566, pOver, 1e-6);
        assertTrue(mu > 2.7 && mu < 3.1, "mu=" + mu);
        assertTrue(MarketScoreMatrix.totalMeanForOver(3.5, 0.566) > mu);
        assertTrue(MarketScoreMatrix.totalMeanForOver(1.5, 0.566) < mu);
    }
}
