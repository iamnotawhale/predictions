package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void compactPromptIncludesFormOverallAndVenueFacts() {
        MatchNarrativeService service = new MatchNarrativeService(null, null, null, null, null, null);
        ExtendedTeamMetrics ext = ExtendedTeamMetrics.builder()
                .ssForm8(1.75, 1.5, 1.1, 1.2)
                .homePpg(2.1)
                .awayPpg(1.4)
                .xPts(15.52, 14.0, 1.52)
                .build();
        ExtendedTeamMetrics awayExt = ExtendedTeamMetrics.builder()
                .homePpg(1.8)
                .awayPpg(0.9)
                .build();
        TeamStatsSnapshot home = new TeamStatsSnapshot(
                "ARS", 1.8, 2.0, 1.6, 0.9, 0.7, 1.1,
                1.7, 1.0, 0.7, 1.9, 1.5, ext, Instant.now()
        );
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "MCI", 1.6, 1.8, 1.4, 1.0, 0.9, 1.1,
                1.6, 1.1, 0.5, 1.7, 1.2, awayExt, Instant.now()
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

        String compact = service.buildCompactPrompt(
                "ARS", "MCI", tip,
                new SoccerStatsMatchPreviewScraper.FixturePair("ARS", "MCI", "Arsenal", "Man City"),
                null, null, home, away
        );

        assertTrue(compact.startsWith("M|Arsenal|ARS|home|Man City|MCI|away"));
        assertTrue(compact.contains("TIP|2:1|"));
        assertTrue(compact.contains("T.ARS|form=overall|"));
        assertTrue(compact.contains("T.MCI|form=overall|"));
        assertTrue(compact.contains("FACTS"));
        assertTrue(compact.contains("Arsenal дома: xG 1.90, 2.10 очков за матч, 2 голов за матч."));
        assertTrue(compact.contains("Man City в гостях: xG 1.20, 0.90 очков за матч, 1.40 голов за матч."));
        assertFalse(compact.contains("SS.HA|"));
        assertFalse(compact.contains("venue=home"));
        assertFalse(compact.contains("|xPts="));
        assertTrue(GroqNarrativeClient.SYSTEM_PROMPT.contains("только из строк FACTS"));
        assertTrue(GroqNarrativeClient.SYSTEM_PROMPT.contains("не повторяй цифры и метрики из FACTS"));
        assertFalse(GroqNarrativeClient.SYSTEM_PROMPT.contains("почти"));
        assertTrue(GroqNarrativeClient.CJK_RETRY_SUFFIX.contains("строк FACTS"));
        assertTrue(GroqNarrativeClient.CJK_RETRY_SUFFIX.contains("один счёт"));
        assertTrue(GroqNarrativeClient.CJK_RETRY_SUFFIX.contains("пустой строкой"));
        assertFalse(GroqNarrativeClient.CJK_RETRY_SUFFIX.contains("venue=home"));
    }

    @Test
    void venueFactsOmitsSideWithoutSampleOrRates() {
        TeamStatsSnapshot emptyHome = new TeamStatsSnapshot(
                "ARS", 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, ExtendedTeamMetrics.empty(), Instant.now()
        );
        ExtendedTeamMetrics awayExt = ExtendedTeamMetrics.builder().awayPpg(0.9).build();
        TeamStatsSnapshot away = new TeamStatsSnapshot(
                "MCI", 1.6, 1.8, 1.4, 1.0, 0.9, 1.1,
                1.6, 1.1, 0.5, 1.7, 1.2, awayExt, Instant.now()
        );
        assertEquals(null, MatchNarrativeCompactEncoder.venueFacts("Arsenal", true, emptyHome));
        assertTrue(MatchNarrativeCompactEncoder.venueFacts("Man City", false, away)
                .contains("Man City в гостях: xG 1.20, 0.90 очков за матч, 1.40 голов за матч."));
    }
}
