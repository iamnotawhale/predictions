package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.ScoreDistribution;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

class RecommendationAnalysisWriterTest {

    @Test
    void writerMentionsTipMostLikelySplitAndMarkets() {
        ScoreDistribution dist = ScoreDistribution.fromNormalizedMatrix(new double[][]{
                {0.08, 0.06, 0.04, 0.02, 0.01, 0.0},
                {0.07, 0.11, 0.07, 0.03, 0.01, 0.0},
                {0.05, 0.08, 0.09, 0.04, 0.02, 0.0},
                {0.03, 0.04, 0.05, 0.03, 0.01, 0.0},
                {0.01, 0.02, 0.02, 0.01, 0.01, 0.0},
                {0.0, 0.01, 0.01, 0.01, 0.0, 0.0}
        });
        TeamStatsSnapshot home = new TeamStatsSnapshot(
                "MUN", 1.5, 1.6, 1.4, 1.3, 1.2, 1.4,
                1.6, 1.4, 0.2, 1.7, 1.5,
                ExtendedTeamMetrics.builder().xPts(12.0, 9.0, -3.0).recentXg(1.8, 1.2, 5).build(),
                Instant.now()
        );
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "AST", 1.4, 1.5, 1.3, 1.5, 1.4, 1.6,
                1.3, 1.8, -0.5, 1.2, 1.4,
                ExtendedTeamMetrics.empty(),
                Instant.now()
        );
        H2hStats h2h = new H2hStats(4, 2, 1, 1, 1.5, 1.2, 2, 1, 0, 1, 1.6, 1.0);
        String text = RecommendationAnalysisWriter.write(
                "MUN", "AST",
                1, 0, 0.09, 0.55,
                1, 1, 0.12,
                1.56, 1.28,
                dist,
                new PoissonScoreModel.MarketOutcome(0.43, 0.26, 0.31),
                home, away,
                home.extendedOrEmpty(), away.extendedOrEmpty(),
                h2h,
                0.2
        );
        assertTrue(text.contains("1:0"));
        assertTrue(text.contains("1:1"));
        assertTrue(text.contains("xG"));
        assertTrue(text.contains("личных"));
        assertTrue(text.contains("Тотал больше 2.5"));
        assertTrue(text.contains("call"));
        assertTrue(text.contains("опирается на рынок"));
        assertFalse(text.contains("STATS_SHAPE"));
        assertFalse(text.contains("λ"));
    }

    @Test
    void strongFavouriteIsNotCalledSlightLean() {
        ScoreDistribution dist = ScoreDistribution.fromNormalizedMatrix(new double[][]{
                {0.02, 0.01, 0.01, 0.0, 0.0, 0.0},
                {0.04, 0.03, 0.02, 0.01, 0.0, 0.0},
                {0.12, 0.08, 0.04, 0.02, 0.01, 0.0},
                {0.15, 0.09, 0.05, 0.02, 0.01, 0.0},
                {0.10, 0.05, 0.03, 0.01, 0.0, 0.0},
                {0.05, 0.02, 0.01, 0.0, 0.0, 0.0}
        });
        String text = RecommendationAnalysisWriter.write(
                "ARS", "HUL",
                2, 0, 0.14, 0.7,
                2, 0, 0.14,
                2.6, 0.7,
                dist,
                new PoissonScoreModel.MarketOutcome(0.78, 0.14, 0.08),
                null, null,
                ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty(),
                null,
                0.1
        );
        assertTrue(text.contains("явный уклон"));
        assertFalse(text.contains("лёгкий уклон"));
    }

    @Test
    void withoutMarketOmitsBookmakerSentence() {
        ScoreDistribution dist = ScoreDistribution.fromNormalizedMatrix(new double[][]{
                {0.08, 0.06, 0.04, 0.02, 0.01, 0.0},
                {0.07, 0.11, 0.07, 0.03, 0.01, 0.0},
                {0.05, 0.08, 0.09, 0.04, 0.02, 0.0},
                {0.03, 0.04, 0.05, 0.03, 0.01, 0.0},
                {0.01, 0.02, 0.02, 0.01, 0.01, 0.0},
                {0.0, 0.01, 0.01, 0.01, 0.0, 0.0}
        });
        String text = RecommendationAnalysisWriter.write(
                "HOM", "AWY",
                1, 1, 0.11, 0.4,
                1, 1, 0.11,
                1.4, 1.3,
                dist,
                null,
                null, null,
                ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty(),
                null,
                0.2
        );
        assertFalse(text.contains("опирается на рынок"));
        assertTrue(text.contains("call"));
    }
}