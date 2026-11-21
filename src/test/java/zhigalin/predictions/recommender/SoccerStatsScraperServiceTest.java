package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

class SoccerStatsScraperServiceTest {

    private final SoccerStatsScraperService scraper = new SoccerStatsScraperService();

    @Test
    void timingOverallIgnoresHomeBlockAndKeepsFirstGoalMinuteSeparate() {
        String html = """
                <html><body>
                <h2>Goals scored / conceded per 15 min. (Total)</h2>
                <table>
                <tr><th>Total</th><th>GP</th><th>0-15</th><th>16-30</th><th>31-45</th><th>46-60</th><th>61-75</th><th>76-90</th><th>1st Half</th><th>2nd Half</th><th>Avg min. GF</th><th>Avg min. GA</th></tr>
                <tr><td>Arsenal</td><td>10</td><td>2-1</td><td>3-0</td><td>0-2</td><td>4-2</td><td>4-0</td><td>2-0</td><td>5-3</td><td>10-2</td><td>50 min.</td><td>38 min.</td></tr>
                <tr><td>Brighton</td><td>10</td><td>0-0</td><td>0-0</td><td>0-0</td><td>0-0</td><td>0-0</td><td>0-0</td><td>0-0</td><td>0-0</td><td>40 min.</td><td>40 min.</td></tr>
                </table>
                <h2>Goals scored / conceded per 15 min. (Home)</h2>
                <table>
                <tr><td>Arsenal</td><td>5</td><td>9-9</td><td>9-9</td><td>9-9</td><td>9-9</td><td>9-9</td><td>9-9</td><td>9-9</td><td>9-9</td><td>10 min.</td><td>10 min.</td></tr>
                <tr><td>Chelsea</td><td>5</td><td>1-0</td><td>1-0</td><td>1-0</td><td>1-0</td><td>1-0</td><td>1-0</td><td>3-0</td><td>3-0</td><td>20 min.</td><td>20 min.</td></tr>
                </table>
                </body></html>
                """;
        Map<String, SoccerStatsScraperService.TimingRow> rows = scraper.parseTimingOverall15(html);
        assertEquals(2, rows.size());
        assertFalse(rows.containsKey("CHE"));
        SoccerStatsScraperService.TimingRow arsenal = rows.get("ARS");
        assertEquals(2 * 100.0 / 15, arsenal.lateScoredPct(), 1e-9);
        assertEquals(0.0, arsenal.lateConcededPct(), 1e-9);
        assertEquals(10 * 100.0 / 15, arsenal.secondHalfGfSharePct(), 1e-9);
        assertEquals(50.0, arsenal.avgGfMinute(), 1e-9);
        assertEquals(38.0, arsenal.avgGaMinute(), 1e-9);

        SoccerStatsScraperService.TimingRow brighton = rows.get("BRI");
        assertNull(brighton.lateScoredPct());
        assertNull(brighton.lateConcededPct());
        assertNull(brighton.secondHalfGfSharePct());

        Map<String, ExtendedTeamMetrics.Builder> patches = new LinkedHashMap<>();
        patches.put("ARS", ExtendedTeamMetrics.builder());
        patches.put("BRI", ExtendedTeamMetrics.builder());
        scraper.mergeTiming(patches, html);
        ExtendedTeamMetrics ars = patches.get("ARS").build();
        assertEquals(50.0, ars.ssAvgGfMinute(), 1e-9);
        assertNull(ars.ssAvgFirstGoalMin());
    }

    @Test
    void formLast8IgnoresHomeBlockWithDifferentPts() {
        String html = """
                <html><body>
                <h2>Last 8 matches</h2>
                <table>
                <tr><th>Team</th><th>GP</th><th>W</th><th>D</th><th>L</th><th>GF</th><th>GA</th><th>GD</th><th>Pts</th><th>Opponents PPG</th></tr>
                <tr><td>1</td><td>Arsenal</td><td>8</td><td>7</td><td>0</td><td>1</td><td>11</td><td>5</td><td>+6</td><td>21</td><td>1.29</td></tr>
                <tr><td>2</td><td>Brighton</td><td>8</td><td>6</td><td>1</td><td>1</td><td>19</td><td>3</td><td>+16</td><td>19</td><td>1.41</td></tr>
                </table>
                <h2>Last 8 matches - Home</h2>
                <table>
                <tr><th>Team</th><th>GP</th><th>W</th><th>D</th><th>L</th><th>GF</th><th>GA</th><th>GD</th><th>Pts</th><th>Opponents PPG</th></tr>
                <tr><td>1</td><td>Arsenal</td><td>8</td><td>1</td><td>0</td><td>7</td><td>99</td><td>50</td><td>+49</td><td>3</td><td>9.99</td></tr>
                <tr><td>2</td><td>Chelsea</td><td>8</td><td>8</td><td>0</td><td>0</td><td>20</td><td>0</td><td>+20</td><td>24</td><td>1.00</td></tr>
                </table>
                </body></html>
                """;
        Map<String, SoccerStatsScraperService.Form8Row> rows = scraper.parseFormLast8(html);
        assertEquals(2, rows.size());
        assertFalse(rows.containsKey("CHE"));
        assertEquals(11.0, rows.get("ARS").gf(), 1e-9);
        assertEquals(21.0, rows.get("ARS").pts(), 1e-9);
        assertEquals(21.0 / 8.0, rows.get("ARS").ppg(), 1e-9);
        assertEquals(1.29, rows.get("ARS").oppPpg(), 1e-9);
    }

    @Test
    void formLast8FallbackUsesFirstGp8StreakWithoutHeading() {
        String html = """
                <html><body>
                <table>
                <tr><td>Arsenal</td><td>4</td><td>4</td><td>0</td><td>0</td><td>5</td><td>0</td><td>+5</td><td>12</td></tr>
                <tr><td>Arsenal</td><td>8</td><td>7</td><td>0</td><td>1</td><td>11</td><td>5</td><td>+6</td><td>21</td></tr>
                <tr><td>Brighton</td><td>8</td><td>6</td><td>1</td><td>1</td><td>19</td><td>3</td><td>+16</td><td>19</td></tr>
                <tr><td>Arsenal</td><td>10</td><td>9</td><td>0</td><td>1</td><td>15</td><td>5</td><td>+10</td><td>27</td></tr>
                <tr><td>Arsenal</td><td>8</td><td>1</td><td>0</td><td>7</td><td>99</td><td>50</td><td>+49</td><td>3</td></tr>
                <tr><td>Chelsea</td><td>8</td><td>8</td><td>0</td><td>0</td><td>20</td><td>0</td><td>+20</td><td>24</td></tr>
                </table>
                </body></html>
                """;
        Map<String, SoccerStatsScraperService.Form8Row> rows = scraper.parseFormLast8(html);
        assertEquals(2, rows.size());
        assertFalse(rows.containsKey("CHE"));
        assertEquals(11.0, rows.get("ARS").gf(), 1e-9);
    }

    @Test
    void formOppPpgNullWithoutOpponentHeader() {
        String html = """
                <html><body>
                <h2>Last 8 matches</h2>
                <table>
                <tr><th>Team</th><th>GP</th><th>W</th><th>D</th><th>L</th><th>GF</th><th>GA</th><th>GD</th><th>Pts</th><th>Extra</th></tr>
                <tr><td>Arsenal</td><td>8</td><td>7</td><td>0</td><td>1</td><td>11</td><td>5</td><td>+6</td><td>21</td><td>1.29</td></tr>
                </table>
                </body></html>
                """;
        Map<String, SoccerStatsScraperService.Form8Row> rows = scraper.parseFormLast8(html);
        assertNull(rows.get("ARS").oppPpg());
    }

    @Test
    void halftimeDoesNotOverwriteExistingHtMetrics() {
        String html = """
                <html><body>
                <h2>First Half table (1-45 min.)</h2>
                <table>
                <tr><td>1</td><td>Arsenal</td><td>10</td><td>3</td><td>6</td><td>1</td><td>5</td><td>3</td><td>+2</td><td>15</td></tr>
                </table>
                <h2>Second Half table (46-90 min.)</h2>
                <table>
                <tr><td>1</td><td>Arsenal</td><td>10</td><td>8</td><td>1</td><td>1</td><td>10</td><td>2</td><td>+8</td><td>25</td></tr>
                </table>
                <h2>First Half table (AT HOME, 1-45 min.)</h2>
                <table>
                <tr><td>1</td><td>Arsenal</td><td>5</td><td>5</td><td>0</td><td>0</td><td>10</td><td>0</td><td>+10</td><td>15</td></tr>
                </table>
                </body></html>
                """;
        ExtendedTeamMetrics existing = ExtendedTeamMetrics.builder()
                .htPpg(1.1)
                .winningAtHtPct(40.0)
                .build();
        TeamStatsSnapshot snap = new TeamStatsSnapshot(
                "ARS", 1, 1, 1, 1, 1, 1, null, null, null, null, null, existing, Instant.now());
        Map<String, ExtendedTeamMetrics.Builder> patches = new LinkedHashMap<>();
        patches.put("ARS", ExtendedTeamMetrics.builder());
        scraper.mergeHalftime(patches, Map.of("ARS", snap), html);
        ExtendedTeamMetrics patch = patches.get("ARS").build();
        assertNull(patch.htPpg());
        assertNull(patch.winningAtHtPct());
        assertEquals(2.5, patch.ssSecondHalfPpg(), 1e-9);

        Map<String, ExtendedTeamMetrics.Builder> emptyPatches = new LinkedHashMap<>();
        emptyPatches.put("ARS", ExtendedTeamMetrics.builder());
        TeamStatsSnapshot bare = new TeamStatsSnapshot(
                "ARS", 1, 1, 1, 1, 1, 1, null, null, null, null, null, null, Instant.now());
        scraper.mergeHalftime(emptyPatches, Map.of("ARS", bare), html);
        ExtendedTeamMetrics filled = emptyPatches.get("ARS").build();
        assertEquals(1.5, filled.htPpg(), 1e-9);
        assertEquals(30.0, filled.winningAtHtPct(), 1e-9);
    }

    @Test
    void mapperShortLabels() {
        assertEquals("MCI", SoccerStatsTeamNameMapper.toTeamCode("Man City").orElseThrow());
        assertEquals("MUN", SoccerStatsTeamNameMapper.toTeamCode("Man Utd").orElseThrow());
        assertEquals("MCI", SoccerStatsTeamNameMapper.toTeamCode("Manchester City").orElseThrow());
    }
}
