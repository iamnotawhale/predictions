package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExplanationBreakdown;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

class MatchNarrativeCompactPromptTest {

    @Test
    void compactPromptIncludesPreviewCellsAndTeamMetrics() {
        MatchNarrativeService service = new MatchNarrativeService(null, null, null, null, null, null);
        ExtendedTeamMetrics ext = ExtendedTeamMetrics.builder()
                .ssScoredFirst(55.0, 2.3)
                .ssLeadDurations(40.0, 25.0)
                .ssTiming(22.0, 18.0, 58.0, 51.0, 48.0)
                .ssForm8(1.75, 1.5, 1.1, 1.2)
                .xPts(15.52, 14.0, 1.52)
                .build();
        TeamStatsSnapshot home = new TeamStatsSnapshot(
                "ARS", 1.8, 2.0, 1.6, 0.9, 0.7, 1.1,
                1.7, 1.0, 0.7, 1.9, 1.5, ext, Instant.now()
        );
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "MCI", 1.6, 1.8, 1.4, 1.0, 0.9, 1.1,
                1.6, 1.1, 0.5, 1.7, 1.5, ExtendedTeamMetrics.empty(), Instant.now()
        );
        MatchRecommendationSnapshot tip = new MatchRecommendationSnapshot(
                1, 12, 2, 1, 1.85, 1.20, 0.11,
                new ExplanationBreakdown(
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        "template",
                        new ExplanationBreakdown.ModelScore(2, 1, 0.12, 0.5, 0.25, 0.25)
                ),
                "summary",
                Instant.now()
        );
        SoccerStatsMatchPreviewScraper.PreviewLine ha = new SoccerStatsMatchPreviewScraper.PreviewLine(
                "ARS", "MCI", "home", "away",
                "100% · 2.00 · 0.40", "75% · 1.80 · 1.00",
                java.util.List.of("100%", "2.00", "0.40", "2.40", "50%"),
                java.util.List.of("75%", "1.80", "1.00", "2.80", "60%")
        );
        SoccerStatsMatchPreviewScraper.PreviewLine ov = new SoccerStatsMatchPreviewScraper.PreviewLine(
                "ARS", "MCI", "all", "all",
                "90% · 1.90", "70% · 1.70",
                java.util.List.of("90%", "1.90"),
                java.util.List.of("70%", "1.70")
        );

        String compact = service.buildCompactPrompt(
                "ARS", "MCI", tip,
                new SoccerStatsMatchPreviewScraper.FixturePair("ARS", "MCI", "Arsenal", "Man City"),
                ha, ov, home, away
        );

        assertTrue(compact.startsWith("M|Arsenal|ARS|home|Man City|MCI|away"));
        assertTrue(compact.contains("TIP|2:1|"));
        assertTrue(compact.contains("MS|2:1|"));
        assertTrue(compact.contains("SS.HA|ARS|home|ptsShare%=100%|gfPm=2.00|gaPm=0.40|tgPm=2.40|o25%=50%"));
        assertTrue(compact.contains("SS.HA|MCI|away|"));
        assertTrue(compact.contains("SS.OV|ARS|all|"));
        assertTrue(compact.contains("T.ARS|form=overall|gfPm=1.80|gaPm=0.90|f8Ppg=1.75"));
        assertTrue(compact.contains("T.ARS|venue=home|gfPm=2|gaPm=0.70"));
        assertFalse(compact.contains("|xPts="));
        assertTrue(compact.contains("T.MCI|form=overall|"));
        assertTrue(compact.contains("T.MCI|venue=away|gfPm=1.40|gaPm=1.10"));
        assertFalse(compact.contains("{"));
        assertFalse(compact.contains("\"homeTeam\""));
        assertTrue(GroqNarrativeClient.SYSTEM_PROMPT.contains("без любых цифр из overall/f8"));
        assertTrue(GroqNarrativeClient.SYSTEM_PROMPT.contains("китайские иероглифы"));
        assertFalse(GroqNarrativeClient.SYSTEM_PROMPT.contains("почти"));
    }

    @Test
    void overallPreviewUsedWhenHomeAwayMissing() {
        MatchNarrativeService service = new MatchNarrativeService(null, null, null, null, null, null);
        MatchRecommendationSnapshot tip = new MatchRecommendationSnapshot(
                1, 12, 2, 1, 1.85, 1.20, 0.11,
                ExplanationBreakdown.empty(),
                "summary",
                Instant.now()
        );
        SoccerStatsMatchPreviewScraper.PreviewLine ov = new SoccerStatsMatchPreviewScraper.PreviewLine(
                "ARS", "MCI", "all", "all",
                "90% · 1.90", "70% · 1.70",
                java.util.List.of("90%", "1.90"),
                java.util.List.of("70%", "1.70")
        );
        String compact = service.buildCompactPrompt(
                "ARS", "MCI", tip, null, null, ov, null, null
        );
        assertTrue(compact.contains("SS.OV|ARS|all|ptsShare%=90%|gfPm=1.90"));
        assertFalse(compact.contains("SS.HA|"));
    }
}
