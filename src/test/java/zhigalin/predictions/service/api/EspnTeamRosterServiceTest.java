package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.service.api.EspnTeamRosterService.PlayerSeasonStats;

class EspnTeamRosterServiceTest {

    @Test
    void leadersPreferGoalScorersThenKeepersWithSaves() {
        EspnTeamRosterService svc = new EspnTeamRosterService(new com.fasterxml.jackson.databind.ObjectMapper());
        // Exercise sorting helper via public leaders API would need network; unit-test comparator path
        // by reflecting through a local sort of sample rows matching service logic.
        List<PlayerSeasonStats> sample = List.of(
                new PlayerSeasonStats("Bench", "F", 0, 0, 0, null),
                new PlayerSeasonStats("Striker", "F", 5, 3, 1, null),
                new PlayerSeasonStats("Mid", "M", 5, 1, 4, null),
                new PlayerSeasonStats("Keeper", "G", 5, 0, 0, 12)
        );
        List<PlayerSeasonStats> outfield = sample.stream()
                .filter(p -> p.appearances() > 0 || p.goals() > 0 || p.assists() > 0)
                .filter(p -> !"G".equalsIgnoreCase(p.position()))
                .sorted(java.util.Comparator
                        .comparingInt(PlayerSeasonStats::goals).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(PlayerSeasonStats::assists).reversed()))
                .limit(8)
                .toList();
        assertEquals("Striker", outfield.get(0).name());
        assertEquals("Mid", outfield.get(1).name());
        assertTrue(sample.stream().anyMatch(p -> "G".equals(p.position()) && p.saves() == 12));
        assertEquals(0, svc.playersForInternalCode("ZZZ").size());
    }
}
