package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecommendationInsightTest {

    private static double[][] matrix() {
        return MarketScoreMatrix.poisson(1.5, 1.1, 5);
    }

    @Test
    void tiersSumToOneAndMatchPickerExpectedPoints() {
        double[][] m = matrix();
        double total = 0;
        for (double[] row : m) {
            for (double p : row) {
                total += p;
            }
        }
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                m[i][j] /= total;
            }
        }
        var t = RecommendationInsight.tiers(m, 1, 0);
        assertEquals(1.0, t.exact() + t.diff() + t.outcome() + t.miss(), 1e-9);
        var ins = RecommendationInsight.build(m, 1, 0, 1.5, 1.1, true);
        assertEquals(ScorePicker.expectedPoints(m, 1, 0), ins.expectedPoints(), 1e-9);
        assertEquals(new RecommendationInsight.Scoring(4, 2, 1, -1), ins.scoring());
        assertEquals(1.0 - t.miss(), ins.pointsChance(), 1e-9);
    }

    @Test
    void reasonsExplainWhenPickDiffersFromMode() {
        double[][] m = matrix();
        var ins = RecommendationInsight.build(m, 2, 0, 1.5, 1.1, false);
        String all = String.join("\n", ins.reasons());
        assertTrue(all.contains("Коэффициентов пока нет"));
        assertTrue(all.contains("Самый вероятный счёт"));
        assertTrue(all.contains("Очки получим с шансом"));
    }
}
