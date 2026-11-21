package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SoccerStatsMatchPreviewScraperTest {

    @Test
    void parsePreviewPairsJoinsByMappedNamesNotOrder() {
        SoccerStatsMatchPreviewScraper scraper = new SoccerStatsMatchPreviewScraper();
        String html = """
                <html><body><table>
                <tr><td>Fr 27 Nov</td><td>home</td><td>Arsenal</td><td>100%</td><td>2.00</td><td>0.40</td><td>2.40</td><td>50%</td></tr>
                <tr><td></td><td>away</td><td>Man City</td><td>75%</td><td>1.80</td><td>1.00</td><td>2.80</td><td>60%</td></tr>
                <tr><td>Sa 28 Nov</td><td>home</td><td>Unknown FC</td><td>10%</td><td>1.00</td><td>1.00</td><td>2.00</td><td>10%</td></tr>
                <tr><td></td><td>away</td><td>Chelsea</td><td>40%</td><td>1.20</td><td>1.40</td><td>2.60</td><td>40%</td></tr>
                </table></body></html>
                """;
        Map<String, SoccerStatsMatchPreviewScraper.PreviewLine> lines = scraper.parsePreviewPairs(html);
        assertTrue(lines.containsKey("ARS-MCI"));
        assertFalse(lines.containsKey("CHE"));
        SoccerStatsMatchPreviewScraper.PreviewLine ars = lines.get("ARS-MCI");
        assertEquals("ARS", ars.homeCode());
        assertEquals("MCI", ars.awayCode());
        assertEquals("home", ars.homeScope());
        assertEquals("away", ars.awayScope());
        assertTrue(ars.homeStats().contains("100%"));
        assertEquals(
                java.util.List.of("100%", "2.00", "0.40", "2.40", "50%"),
                ars.homeCells()
        );
        assertFalse(ars.homeCells().stream().anyMatch(SoccerStatsMatchPreviewScraper::looksLikeKickoffDate));
    }
}
