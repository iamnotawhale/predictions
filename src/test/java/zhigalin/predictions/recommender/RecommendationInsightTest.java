package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

class RecommendationInsightTest {

    private static double[][] matrix() {
        return MarketScoreMatrix.poisson(1.5, 1.1, 5);
    }

    private static double[][] normalized() {
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
        return m;
    }

    @Test
    void tiersSumToOneAndMatchPickerExpectedPoints() {
        double[][] m = normalized();
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
        assertFalse(all.contains("Очки получим с шансом"));
        assertEquals(3, ins.reasons().size());
    }

    @Test
    void emptySoccerStatsKeepsThreeBaselineReasons() {
        double[][] m = normalized();
        var ins = RecommendationInsight.build(m, 2, 0, 1.5, 1.1, true,
                ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty(), "TOT", "IPS");
        assertEquals(3, ins.reasons().size());
        String all = String.join("\n", ins.reasons());
        assertTrue(all.contains("Букмекеры"));
        assertTrue(all.contains("Ждём около"));
        assertTrue(all.contains("Самый вероятный счёт") || all.contains("самый вероятный счёт"));
    }

    @Test
    void smallGapDoesNotAddSoccerStatsReason() {
        ExtendedTeamMetrics home = ExtendedTeamMetrics.builder().ssScoredFirst(50.0, null).build();
        ExtendedTeamMetrics away = ExtendedTeamMetrics.builder().ssScoredFirst(55.0, null).build();
        var ins = RecommendationInsight.build(normalized(), 2, 0, 1.5, 1.1, true, home, away, "TOT", "IPS");
        assertEquals(3, ins.reasons().size());
        assertFalse(String.join("\n", ins.reasons()).contains("открывает счёт"));
    }

    @Test
    void qualifyingGapAddsOneContrastAndKeepsModeTradeoff() {
        ExtendedTeamMetrics home = ExtendedTeamMetrics.builder().ssScoredFirst(60.0, null).build();
        ExtendedTeamMetrics away = ExtendedTeamMetrics.builder().ssScoredFirst(30.0, null).build();
        var ins = RecommendationInsight.build(normalized(), 2, 0, 1.5, 1.1, true, home, away, "TOT", "IPS");
        assertEquals(3, ins.reasons().size());
        String all = String.join("\n", ins.reasons());
        assertTrue(all.contains("TOT чаще открывает счёт"));
        assertTrue(all.contains("60%") && all.contains("30%"));
        assertTrue(all.contains("Самый вероятный счёт") || all.contains("выгоднее"));
        assertFalse(all.contains("Ждём около"));
    }

    @Test
    void ogsFallbackUsedWhenScoredFirstNull() {
        ExtendedTeamMetrics home = ExtendedTeamMetrics.builder().ssFirstGoal(58.0, null, null).build();
        ExtendedTeamMetrics away = ExtendedTeamMetrics.builder().ssFirstGoal(40.0, null, null).build();
        String line = RecommendationInsight.soccerStatsContrast(home, away, "ARS", "CHE");
        assertTrue(line != null && line.contains("ARS") && line.contains("открывает счёт"));
    }

    @Test
    void oneSideNullSkipsContrast() {
        ExtendedTeamMetrics home = ExtendedTeamMetrics.builder().ssLeadDurations(55.0, 20.0).build();
        assertEquals(null, RecommendationInsight.soccerStatsContrast(home, ExtendedTeamMetrics.empty(), "ARS", "CHE"));
        assertEquals(null, RecommendationInsight.soccerStatsContrast(home, home, "ARS", null));
    }

    @Test
    void venueFormReasonUsesHomeAndAwaySplits() {
        ExtendedTeamMetrics homeExt = ExtendedTeamMetrics.builder().homePpg(2.1).awayPpg(1.0).build();
        ExtendedTeamMetrics awayExt = ExtendedTeamMetrics.builder().homePpg(1.5).awayPpg(0.9).build();
        TeamStatsSnapshot home = new TeamStatsSnapshot(
                "LIV", 2.1, 2.2, 2.0, 0.9, 0.8, 1.0,
                2.0, 0.9, 1.1, 1.95, 1.4, homeExt, Instant.now()
        );
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "MUN", 1.2, 1.4, 1.1, 1.7, 1.5, 1.8,
                1.3, 1.6, -0.3, 1.4, 1.15, awayExt, Instant.now()
        );
        String line = RecommendationInsight.venueFormReason(home, away, "LIV", "MUN");
        assertTrue(line.contains("LIV дома: xG 1.95, 2.10 очк./матч, 2.20 гол./матч."));
        assertTrue(line.contains("MUN в гостях: xG 1.15, 0.90 очк./матч, 1.10 гол./матч."));
        var ins = RecommendationInsight.build(normalized(), 2, 1, 1.9, 1.1, true, home, away);
        assertEquals(3, ins.reasons().size());
        assertEquals("LIV дома: xG 1.95, 2.10 очк./матч, 2.20 гол./матч.", ins.reasons().get(1));
        assertEquals("MUN в гостях: xG 1.15, 0.90 очк./матч, 1.10 гол./матч.", ins.reasons().get(2));
    }

    @Test
    void venueFormReasonNullWhenNoVenueEvidence() {
        TeamStatsSnapshot emptyHome = new TeamStatsSnapshot(
                "LIV", 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, ExtendedTeamMetrics.empty(), Instant.now()
        );
        ExtendedTeamMetrics awayExt = ExtendedTeamMetrics.builder().awayPpg(0.9).build();
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "MUN", 1.2, 1.4, 1.1, 1.7, 1.5, 1.8,
                1.3, 1.6, -0.3, 1.4, 1.15, awayExt, Instant.now()
        );
        assertEquals(null, RecommendationInsight.venueFormReason(emptyHome, away, "LIV", "MUN"));
    }

    @Test
    void qualifyingPercentBeatsLargerMinuteGap() {
        ExtendedTeamMetrics home = ExtendedTeamMetrics.builder()
                .ssScoredFirst(50.0, null)
                .ssFirstGoal(null, null, 20.0)
                .build();
        ExtendedTeamMetrics away = ExtendedTeamMetrics.builder()
                .ssScoredFirst(38.0, null)
                .ssFirstGoal(null, null, 29.0)
                .build();
        String line = RecommendationInsight.soccerStatsContrast(home, away, "TOT", "IPS");
        assertTrue(line != null && line.contains("открывает счёт"));
        assertFalse(line.contains("раньше открывает"));
    }
}
