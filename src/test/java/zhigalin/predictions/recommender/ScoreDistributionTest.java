package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.recommender.model.ScoreDistribution;

class ScoreDistributionTest {

    @Test
    void fromNormalizedMatrixSumsToOneAndTipIsArgmax() {
        double[][] matrix = {
                {0.05, 0.04, 0.03, 0.01, 0.005, 0.005},
                {0.08, 0.12, 0.07, 0.03, 0.01, 0.005},
                {0.06, 0.09, 0.08, 0.04, 0.02, 0.01},
                {0.03, 0.04, 0.03, 0.02, 0.01, 0.005},
                {0.01, 0.02, 0.01, 0.01, 0.005, 0.002},
                {0.005, 0.005, 0.005, 0.002, 0.001, 0.001}
        };
        double sum = 0;
        for (double[] row : matrix) {
            for (double p : row) {
                sum += p;
            }
        }
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix[h].length; a++) {
                matrix[h][a] /= sum;
            }
        }

        ScoreDistribution dist = ScoreDistribution.fromNormalizedMatrix(matrix);
        double total = dist.homeWin() + dist.draw() + dist.awayWin();
        assertEquals(1.0, total, 1e-9);
        assertTrue(dist.hasMatrix());
        assertEquals(6, dist.topScores().size());
        assertEquals(1, dist.topScores().getFirst().home());
        assertEquals(1, dist.topScores().getFirst().away());
        assertEquals(1.0 - matrix[0][0], dist.over05(), 1e-12);
        assertTrue(dist.over05() >= dist.over15());
        assertTrue(dist.over15() >= dist.over25());
        assertTrue(dist.over25() >= dist.over35());
    }

    @Test
    void recommendResultIncludesDistributionMatchingTip() {
        FootyStatsTeamSnapshot home = team("ARS", 1.6, 1.7, 1.4, 1.0, 0.9, 1.1);
        FootyStatsTeamSnapshot away = team("CHE", 1.3, 1.4, 1.2, 1.2, 1.1, 1.3);
        FootyStatsLeagueSnapshot league = new FootyStatsLeagueSnapshot(
                5, 1.48, 1.25, 1.25, 1.48, Instant.now()
        );

        PoissonScoreModel.Result result = PoissonScoreModel.recommend(
                2.10, 3.40, 3.50, home, away, league
        );

        ScoreDistribution dist = result.distribution();
        assertTrue(dist != null && dist.hasMatrix());
        double oneX2 = dist.homeWin() + dist.draw() + dist.awayWin();
        assertEquals(1.0, oneX2, 1e-6);

        double matrixSum = 0;
        for (var row : dist.matrix()) {
            for (Double p : row) {
                matrixSum += p;
            }
        }
        assertEquals(1.0, matrixSum, 1e-6);

        double tipCell = dist.matrix().get(result.recommendedHome()).get(result.recommendedAway());
        assertEquals(result.scoreProbability(), tipCell, 1e-9);
        assertEquals(result.recommendedHome(), dist.topScores().getFirst().home());
        assertEquals(result.recommendedAway(), dist.topScores().getFirst().away());
        assertTrue(result.explanation().distribution() != null);
    }

    private static FootyStatsTeamSnapshot team(
            String code,
            double so, double sh, double sa,
            double co, double ch, double ca
    ) {
        return new FootyStatsTeamSnapshot(
                code, so, sh, sa, co, ch, ca,
                1.4, 1.1, 0.3, 1.5, 1.2,
                FootyStatsExtendedMetrics.empty(),
                Instant.now()
        );
    }
}
