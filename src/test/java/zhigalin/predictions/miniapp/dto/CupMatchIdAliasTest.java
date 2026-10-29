package zhigalin.predictions.miniapp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;

class CupMatchIdAliasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void oldClientBonusMatchIdIsReadAsCupMatchId() throws Exception {
        PredictRequest request = mapper.readValue(
                "{\"homeCode\":\"LIV\",\"awayCode\":\"CHE\",\"homeScore\":2,\"awayScore\":1,\"bonusMatchId\":42}",
                PredictRequest.class);
        assertEquals(42, request.cupMatchId());
    }

    @Test
    void newClientCupMatchIdIsStillRead() throws Exception {
        PredictRequest request = mapper.readValue(
                "{\"homeCode\":\"LIV\",\"awayCode\":\"CHE\",\"homeScore\":2,\"awayScore\":1,\"cupMatchId\":42}",
                PredictRequest.class);
        assertEquals(42, request.cupMatchId());
    }

    @Test
    void matchItemCarriesBothCupMatchIdAndLegacyBonusMatchId() throws Exception {
        RecordComponent[] components = MatchItem.class.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Class<?> type = components[i].getType();
            if (components[i].getName().equals("cupMatchId")) {
                args[i] = 7;
            } else if (type == boolean.class) {
                args[i] = false;
            } else if (type == int.class) {
                args[i] = 0;
            } else if (type == long.class) {
                args[i] = 0L;
            } else if (type == double.class) {
                args[i] = 0.0;
            } else {
                args[i] = null;
            }
        }
        MatchItem item = (MatchItem) MatchItem.class.getDeclaredConstructors()[0].newInstance(args);
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"cupMatchId\":7"), json);
        assertTrue(json.contains("\"bonusMatchId\":7"), json);
    }
}
