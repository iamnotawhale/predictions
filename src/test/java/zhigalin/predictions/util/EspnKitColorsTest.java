package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EspnKitColorsTest {

    private static final String SUMMARY = """
            {"rosters":[
              {"homeAway":"home","uniform":{"type":"home","color":"cf0221","alternateColor":"7f0a22"}},
              {"homeAway":"away","uniform":{"type":"third","color":"FCEEC1","alternateColor":"1b4879"}}
            ]}
            """;

    @Test
    void readsKitForEachSideAndNormalisesHex() throws Exception {
        JsonNode root = new ObjectMapper().readTree(SUMMARY);
        assertEquals(new EspnKitColors.Kit("#cf0221", "#7f0a22"), EspnKitColors.load(root, "home"));
        assertEquals(new EspnKitColors.Kit("#fceec1", "#1b4879"), EspnKitColors.load(root, "away"));
    }

    @Test
    void missingOrInvalidDataGivesNull() throws Exception {
        JsonNode root = new ObjectMapper().readTree("{\"rosters\":[{\"homeAway\":\"away\",\"uniform\":{\"color\":\"zz\"}}]}");
        assertNull(EspnKitColors.load(root, "away"));
        assertNull(EspnKitColors.load(root, "home"));
        assertNull(EspnKitColors.load(null, "home"));
    }

    @Test
    void missingAlternateColorIsKeptAsNull() throws Exception {
        JsonNode root = new ObjectMapper().readTree("{\"rosters\":[{\"homeAway\":\"home\",\"uniform\":{\"color\":\"cf0221\"}}]}");
        assertEquals(new EspnKitColors.Kit("#cf0221", null), EspnKitColors.load(root, "home"));
    }
}
