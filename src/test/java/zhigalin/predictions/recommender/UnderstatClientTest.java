package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.UnderstatClient.UnderstatMatch;

class UnderstatClientTest {

    @Test
    void parseDatesDataExtractsFinishedXg() {
        String json = "["
                + "{\"id\":\"1\",\"isResult\":true,"
                + "\"h\":{\"id\":\"83\",\"title\":\"Arsenal\",\"short_title\":\"ARS\"},"
                + "\"a\":{\"id\":\"80\",\"title\":\"Chelsea\",\"short_title\":\"CHE\"},"
                + "\"goals\":{\"h\":\"2\",\"a\":\"1\"},\"xG\":{\"h\":\"1.85\",\"a\":\"0.92\"},"
                + "\"datetime\":\"2026-08-15 15:00:00\"},"
                + "{\"id\":\"2\",\"isResult\":false,"
                + "\"h\":{\"id\":\"83\",\"title\":\"Arsenal\",\"short_title\":\"ARS\"},"
                + "\"a\":{\"id\":\"87\",\"title\":\"Liverpool\",\"short_title\":\"LIV\"},"
                + "\"goals\":{\"h\":\"0\",\"a\":\"0\"},\"xG\":{\"h\":\"0\",\"a\":\"0\"},"
                + "\"datetime\":\"2026-08-22 15:00:00\"}"
                + "]";
        String escaped = json.replace("\\", "\\\\").replace("\"", "\\\"");
        String html = "<script>var datesData = JSON.parse('" + escaped + "');</script>";

        UnderstatClient client = new UnderstatClient(new ObjectMapper());
        List<UnderstatMatch> matches = client.parseDatesData(html);
        assertEquals(1, matches.size());
        assertEquals("ARS", matches.getFirst().homeCode());
        assertEquals("CHE", matches.getFirst().awayCode());
        assertEquals(1.85, matches.getFirst().homeXg());
        assertEquals(0.92, matches.getFirst().awayXg());
    }

    @Test
    void looksOfflineDetectsMaintenancePage() {
        assertTrue(UnderstatClient.looksOffline("<html>We will come back soon</html>"));
        assertTrue(UnderstatClient.looksOffline("<link href=\"/css/errors.css\">"));
    }

    @Test
    void parseDatesDataSoftFailsOnMissingBlock() {
        UnderstatClient client = new UnderstatClient(new ObjectMapper());
        assertTrue(client.parseDatesData("<html>no data</html>").isEmpty());
    }
}
