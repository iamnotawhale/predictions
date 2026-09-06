package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EspnTeamTotalsClientTest {

    @Test
    void pickMainLinePrefersBalancedPair() {
        Map<String, List<Double>> lines = Map.of(
                "0.5", List.of(1.20, 3.73),
                "1.5", List.of(2.03, 1.64),
                "2.5", List.of(4.31, 1.15)
        );
        assertEquals(1.5, EspnTeamTotalsClient.pickMainLine(lines));
    }

    @Test
    void pickMainLineEmptyReturnsNull() {
        assertNull(EspnTeamTotalsClient.pickMainLine(Map.of()));
        assertNull(EspnTeamTotalsClient.pickMainLine(null));
    }
}
