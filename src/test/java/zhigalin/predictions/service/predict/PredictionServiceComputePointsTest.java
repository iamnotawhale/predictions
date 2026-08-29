package zhigalin.predictions.service.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PredictionServiceComputePointsTest {

    @ParameterizedTest
    @CsvSource({
            "2,1,2,1,4",
            "2,1,3,2,2",
            "2,1,1,0,2",
            "2,1,3,0,1",
            "2,1,1,3,-1",
            "1,1,2,2,2",
            "1,1,0,0,2",
            "1,1,1,0,-1",
            "0,2,1,3,2",
            "0,2,0,1,1"
    })
    void computePoints_scoringRules(int rh, int ra, int ph, int pa, int expected) {
        assertEquals(expected, PredictionService.computePoints(rh, ra, ph, pa));
    }

    @Test
    void computePoints_missingPredictIsMinusOne() {
        assertEquals(-1, PredictionService.computePoints(1, 0, null, 0));
        assertEquals(-1, PredictionService.computePoints(1, 0, 1, null));
        assertEquals(-1, PredictionService.computePoints(1, 0, null, null));
    }

    @Test
    void computePoints_missingRealScoreIsZeroWhenPredictExists() {
        assertEquals(0, PredictionService.computePoints(null, 1, 1, 0));
        assertEquals(0, PredictionService.computePoints(1, null, 1, 0));
        assertEquals(0, PredictionService.computePoints(null, null, 1, 0));
    }
}
