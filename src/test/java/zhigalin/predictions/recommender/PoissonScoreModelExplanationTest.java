package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.PoissonScoreModel.MarketOutcome;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

class PoissonScoreModelExplanationTest {

    @Test
    void attackAndDefenseLabelsAreHumanReadable() {
        assertEquals("сильная", PoissonScoreModel.attackLabel(1.40));
        assertEquals("средняя", PoissonScoreModel.attackLabel(1.00));
        assertEquals("слабая", PoissonScoreModel.attackLabel(0.60));
        assertEquals("дырявая", PoissonScoreModel.defenseLabel(1.40));
        assertEquals("жёсткая", PoissonScoreModel.defenseLabel(0.60));
    }

    @Test
    void venueScoredPhraseDoesNotBorrowOverallAsAway() {
        // NEW: only home sample (2 scored at home), no away games
        assertEquals(
                "NEW в гостях ещё не играли",
                PoissonScoreModel.venueScoredPhrase("NEW", "в гостях", 0, 0, 2.0)
        );
        assertEquals(
                "NEW в гостях забивает 1.5",
                PoissonScoreModel.venueScoredPhrase("NEW", "в гостях", 1.5, 1.0, 1.5)
        );
    }

    @Test
    void humanExplanationAvoidsRawModelJargonAndFalseAwayRate() {
        FootyStatsTeamSnapshot tot = team("TOT", 0, 0, 0, 1.2, 0.8, 0.4);
        // NEW only played home: overall/home goals, zero away venue
        FootyStatsTeamSnapshot neu = team("NEW", 2.0, 2.0, 0, 3.0, 3.0, 0);
        FootyStatsExtendedMetrics homeExt = FootyStatsExtendedMetrics.builder()
                .formBtts(0.0, 0.0, 0.0)
                .formCs(0.0, 0.0, 0.0)
                .seasonScored(0.0, 0.0, null)
                .over25(0.0, 0.0, 0.0)
                .build();
        FootyStatsExtendedMetrics awayExt = FootyStatsExtendedMetrics.builder()
                .formBtts(0.0, 0.0, 0.0)
                .formCs(0.0, 0.0, 0.0)
                .seasonScored(2.0, 2.0, null)
                .over25(0.0, 0.0, 0.0)
                .build();

        List<String> lines = PoissonScoreModel.buildHumanExplanation(
                "TOT",
                "NEW",
                1.09,
                1.22,
                1.00,
                1.20,
                1.0,
                1.0,
                0.7,
                0.24,
                1.56,
                0.29,
                1.66,
                tot,
                neu,
                homeExt,
                awayExt,
                new MarketOutcome(0.44, 0.25, 0.30),
                2.15,
                3.75,
                3.15,
                0,
                1,
                0.122
        );

        String joined = String.join("\n", lines);
        assertFalse(joined.contains("λ"));
        assertFalse(joined.contains("BTTS"));
        assertFalse(joined.contains("SoccerSTATS"));
        assertTrue(joined.contains("Ожидаемые голы"));
        assertTrue(joined.contains("NEW в гостях ещё не играли"));
        assertFalse(joined.contains("NEW в гостях забивает 2"));
        assertTrue(joined.contains("Букмекеры"));
        assertTrue(joined.contains("Самый вероятный счёт — 0:1"));
    }

    @Test
    void blankAwayCellDoesNotShiftHomeIntoAwayColumn() {
        Element row = Jsoup.parseBodyFragment("""
                <table><tbody><tr>
                  <td class="team"><a href="/clubs/x">Newcastle United</a></td>
                  <td>1</td><td>2.00</td><td>2.00</td><td></td>
                </tr></tbody></table>
                """).selectFirst("tr");
        List<String> cells = FootyStatsTableParser.dataCells(row);
        assertEquals(List.of("1", "2.00", "2.00", ""), cells);
        assertEquals(1, FootyStatsTableParser.mpOffset(cells));
        assertEquals(2.0, FootyStatsTableParser.cellAfterMp(cells, 0));
        assertEquals(2.0, FootyStatsTableParser.cellAfterMp(cells, 1));
        assertNull(FootyStatsTableParser.cellAfterMp(cells, 2));
    }

    private static FootyStatsTeamSnapshot team(
            String code,
            double scoredOverall,
            double scoredHome,
            double scoredAway,
            double concededOverall,
            double concededHome,
            double concededAway
    ) {
        return new FootyStatsTeamSnapshot(
                code,
                scoredOverall,
                scoredHome,
                scoredAway,
                concededOverall,
                concededHome,
                concededAway,
                null,
                null,
                null,
                null,
                null,
                FootyStatsExtendedMetrics.empty(),
                Instant.parse("2026-08-29T12:00:00Z")
        );
    }
}
