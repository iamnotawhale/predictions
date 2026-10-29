package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CupOddsModelTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-29T19:45:00Z");

    private static JsonNode root() throws Exception {
        try (InputStream in = CupOddsModelTest.class.getResourceAsStream("/espn/cup-odds-new-eve.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    private static CupOddsModel.Odds odds() throws Exception {
        return CupOddsModel.fromEspnSummary(root()).orElseThrow();
    }

    private static double expectedHomeGoals(double[][] matrix) {
        double sum = 0;
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix[h].length; a++) {
                sum += h * matrix[h][a];
            }
        }
        return sum;
    }

    private static double expectedTotalGoals(double[][] matrix) {
        double sum = 0;
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix[h].length; a++) {
                sum += (h + a) * matrix[h][a];
            }
        }
        return sum;
    }

    @Test
    void parsesMoneylineAndTotalsFromEspnSummary() throws Exception {
        CupOddsModel.Odds odds = odds();
        assertEquals(130.0, odds.home(), 1e-9);
        assertEquals(240.0, odds.draw(), 1e-9);
        assertEquals(185.0, odds.away(), 1e-9);
        assertEquals(2.5, odds.overLine(), 1e-9);
        assertEquals(-155.0, odds.over(), 1e-9);
        assertEquals(120.0, odds.under(), 1e-9);
    }

    @Test
    void americanOddsConvertToDecimal() {
        assertEquals(2.30, CupOddsModel.decimal(130), 1e-9);
        assertEquals(3.40, CupOddsModel.decimal(240), 1e-9);
        assertEquals(1.645, CupOddsModel.decimal(-155), 1e-3);
    }

    @Test
    void americanOddsConvertToImpliedProbability() {
        assertEquals(0.5, CupOddsModel.implied(-100), 1e-9);
        assertEquals(0.4348, CupOddsModel.implied(130), 1e-4);
        assertEquals(0.6078, CupOddsModel.implied(-155), 1e-4);
    }

    @Test
    void parsesFormAndHeadToHeadFromEspnSummary() throws Exception {
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root(), KICKOFF);
        assertEquals(4, form.home().goalsFor(), 1e-9);
        assertEquals(6, form.home().goalsAgainst(), 1e-9);
        assertEquals(5, form.home().matches());
        assertEquals(6, form.away().goalsFor(), 1e-9);
        assertEquals(10, form.away().goalsAgainst(), 1e-9);
        assertEquals(8, form.h2h().homeGoals(), 1e-9);
        assertEquals(7, form.h2h().awayGoals(), 1e-9);
        assertEquals(5, form.h2h().matches());
    }

    @Test
    void marketOutcomeAndTotalArePreservedAfterFormTilt() throws Exception {
        CupOddsModel.Odds odds = odds();
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root(), KICKOFF);
        PoissonScoreModel.MarketOutcome market = CupOddsModel.market(odds);
        double[][] plain = CupOddsModel.scoreMatrix(market, CupOddsModel.totalTarget(odds), null);
        double[][] tilted = CupOddsModel.scoreMatrix(market, CupOddsModel.totalTarget(odds), form);
        CupOddsModel.Tip tip = CupOddsModel.recommend(odds, form).orElseThrow();
        assertEquals(market.homeWin(), tip.pHome(), 1e-9);
        assertEquals(market.draw(), tip.pDraw(), 1e-9);
        assertEquals(market.awayWin(), tip.pAway(), 1e-9);
        assertTrue(tip.formApplied());
        assertEquals(expectedTotalGoals(plain), expectedTotalGoals(tilted), 0.05);
    }

    @Test
    void strongerRecentAttackShiftsGoalsTowardsThatTeam() throws Exception {
        CupOddsModel.Odds odds = odds();
        PoissonScoreModel.MarketOutcome market = CupOddsModel.market(odds);
        double total = CupOddsModel.totalTarget(odds);
        CupOddsModel.Form homeOnFire = new CupOddsModel.Form(
                new CupOddsModel.TeamForm(30, 5, 5, "Premier League"),
                new CupOddsModel.TeamForm(5, 30, 5, "Premier League"), null);
        double plain = expectedHomeGoals(CupOddsModel.scoreMatrix(market, total, null));
        double tilted = expectedHomeGoals(CupOddsModel.scoreMatrix(market, total, homeOnFire));
        assertTrue(tilted > plain, "plain=" + plain + " tilted=" + tilted);
    }

    @Test
    void mixedLeagueFormIsDroppedAndOddsOnlyResultIsKept() throws Exception {
        ObjectNode root = (ObjectNode) root();
        for (JsonNode entry : root.get("lastFiveGames")) {
            if ("361".equals(entry.path("team").path("id").asText())) {
                for (JsonNode event : entry.get("events")) {
                    ((ObjectNode) event).put("leagueAbbreviation", "English League One");
                }
            }
        }
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root, KICKOFF);
        assertNull(form.home());
        assertNull(form.away());
        CupOddsModel.Odds odds = CupOddsModel.fromEspnSummary(root).orElseThrow();
        CupOddsModel.Tip withForm = CupOddsModel.recommend(odds, form).orElseThrow();
        CupOddsModel.Tip h2hOnly = CupOddsModel.recommend(odds, new CupOddsModel.Form(null, null, form.h2h())).orElseThrow();
        assertFalse(withForm.formApplied());
        assertEquals(h2hOnly.home(), withForm.home());
        assertEquals(h2hOnly.away(), withForm.away());
        assertEquals(h2hOnly.expectedPoints(), withForm.expectedPoints(), 1e-9);
    }

    @Test
    void emptyOrNonNumericAndExtraTimeScoresAreExcluded() throws Exception {
        ObjectNode root = (ObjectNode) root();
        JsonNode eve = root.get("lastFiveGames").get(0).get("events");
        ((ObjectNode) eve.get(0)).put("homeTeamScore", "");
        ((ObjectNode) eve.get(1)).put("awayAggregateScore", "5");
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root, KICKOFF);
        assertEquals(3, form.home().matches());
    }

    @Test
    void futureDatedFormEventsAreExcluded() throws Exception {
        ObjectNode root = (ObjectNode) root();
        ((ObjectNode) root.get("lastFiveGames").get(0).get("events").get(0)).put("gameDate", "2026-11-20T15:00Z");
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root, KICKOFF);
        assertEquals(4, form.home().matches());
    }

    @Test
    void pickMaximisesExpectedCupPoints() throws Exception {
        CupOddsModel.Odds odds = odds();
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root(), KICKOFF);
        CupOddsModel.Tip tip = CupOddsModel.recommend(odds, form).orElseThrow();
        double[][] matrix = CupOddsModel.scoreMatrix(CupOddsModel.market(odds), CupOddsModel.totalTarget(odds), form);
        assertEquals(CupOddsModel.expectedCupPoints(matrix, tip.home(), tip.away()), tip.expectedPoints(), 1e-9);
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix.length; a++) {
                assertTrue(CupOddsModel.expectedCupPoints(matrix, h, a) <= tip.expectedPoints() + 1e-9);
            }
        }
    }

    @Test
    void missingMoneylineGivesNoTip() throws Exception {
        assertFalse(CupOddsModel.recommend(new CupOddsModel.Odds(null, 240.0, 185.0, 2.5, -155.0, 120.0)).isPresent());
        assertEquals(Optional.empty(), CupOddsModel.fromEspnSummary(null));
    }

    @Test
    void missingTotalsFallBackToDefaultTotal() {
        CupOddsModel.Odds noTotals = new CupOddsModel.Odds(130.0, 240.0, 185.0, null, null, null);
        assertTrue(CupOddsModel.recommend(noTotals).isPresent());
    }
}
