package zhigalin.predictions.service.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;

class FlooredPointsAndScoringModesTest {

    @Test
    void runningFloorMatchesUserExample() {
        assertEquals(0, FlooredPointsCalculator.applyFloor(List.of(-1, 2, -1, -1, -1)));
    }

    @Test
    void cumulativeWeekSnapshotsFoldCupsIntoActiveWeek() {
        LocalDateTime t0 = LocalDateTime.of(2026, 8, 1, 12, 0);
        List<PointEvent> events = List.of(
                new PointEvent("u", 1, t0, 1, 1, 4, false),
                new PointEvent("u", 1, t0.plusDays(1), 2, 1, 2, false),
                new PointEvent("u", 1, t0.plusDays(3), 3, 0, 2, true),
                new PointEvent("u", 1, t0.plusDays(7), 4, 2, 1, false)
        );
        Map<Integer, Integer> snaps = FlooredPointsService.cumulativeWeekSnapshots(events);
        assertEquals(8, snaps.get(1));
        assertEquals(9, snaps.get(2));
    }

    @Test
    void weekBonusStartsFromWeekFive() {
        assertEquals(false, UserWeekBonusMatchService.isEligibleWeek(4));
        assertEquals(true, UserWeekBonusMatchService.isEligibleWeek(5));
        assertEquals(true, UserWeekBonusMatchService.isEligibleWeek(6));
    }

    @Test
    void runningFloorForgivesEarlyDebt() {
        assertEquals(4, FlooredPointsCalculator.applyFloor(List.of(-1, -1, 4)));
        assertEquals(2, Math.max(0, -1 + -1 + 4)); // naive sum differs
    }

    @Test
    void weekStandingsUseRawSumNotFloor() {
        assertEquals(-2, List.of(-1, -1).stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, FlooredPointsCalculator.applyFloor(List.of(-1, -1)));
        assertEquals(
                Map.of("a", -2, "b", 0),
                FlooredPointsService.sumProvisional(Map.of(
                        "a", List.of(-1, -1),
                        "b", List.of(1, -1)
                ))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "2,1,2,1,5",
            "2,1,3,2,3",
            "2,1,3,0,2",
            "2,1,0,1,0",
            "2,1,,, -1"
    })
    void weekBonusScoring(Integer rh, Integer ra, Integer ph, Integer pa, int expected) {
        assertEquals(expected, PredictionService.computePoints(ScoringMode.EPL_WEEK_BONUS, rh, ra, ph, pa));
    }

    @ParameterizedTest
    @CsvSource({
            "2,1,2,1,2",
            "2,1,3,2,1",
            "2,1,3,0,1",
            "2,1,0,1,0",
            "2,1,,,0"
    })
    void cupScoring(Integer rh, Integer ra, Integer ph, Integer pa, int expected) {
        assertEquals(expected, PredictionService.computePoints(ScoringMode.CUP, rh, ra, ph, pa));
    }
}
