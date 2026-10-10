package zhigalin.predictions.recommender;

import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

/**
 * Chooses which score to recommend from a normalized score-probability matrix.
 * <p>
 * The game pays 4 / 2 / 1 / −1 (exact score / same goal difference / same outcome / miss), so the
 * score that maximises <em>expected points</em> is not the single most likely score: a back-test on
 * three EPL seasons gave +0.14 points per match over picking the mode (see docs/recommender-backtest.md).
 * Payoffs come from {@link PredictionService#computePoints}, so the picker follows the scoring rules.
 */
final class ScorePicker {

    /** A candidate score with its expected points and its own probability. */
    record Pick(int home, int away, double expectedPoints, double probability) {
    }

    private ScorePicker() {
    }

    /** Score with the highest expected EPL points; ties go to the lower home, then lower away score. */
    static Pick bestByExpectedPoints(double[][] matrix) {
        int n = matrix.length;
        Pick best = null;
        for (int h = 0; h < n; h++) {
            for (int a = 0; a < n; a++) {
                double ev = expectedPoints(matrix, h, a);
                if (best == null || ev > best.expectedPoints() + 1e-12) {
                    best = new Pick(h, a, ev, matrix[h][a]);
                }
            }
        }
        return best;
    }

    /** The single most probable score (the previous recommendation rule), for explanation text. */
    static Pick mostLikely(double[][] matrix) {
        int n = matrix.length;
        Pick best = null;
        for (int h = 0; h < n; h++) {
            for (int a = 0; a < n; a++) {
                if (best == null || matrix[h][a] > best.probability()) {
                    best = new Pick(h, a, expectedPoints(matrix, h, a), matrix[h][a]);
                }
            }
        }
        return best;
    }

    /** Expected EPL points of predicting {@code h:a} when the real score follows {@code matrix}. */
    static double expectedPoints(double[][] matrix, int h, int a) {
        double ev = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (matrix[i][j] > 0) {
                    ev += matrix[i][j] * PredictionService.computePoints(ScoringMode.EPL, i, j, h, a);
                }
            }
        }
        return ev;
    }
}
