package zhigalin.predictions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;

class MatchKickoffSyncTest {

    @Test
    void resolvePrefersEspnIdThenHomeAwayAmongNsPst() {
        Team home = Team.builder().id(10).code("AAA").name("A").build();
        Team away = Team.builder().id(20).code("BBB").name("B").build();
        DaoUtil.TEAMS.put(10, home);
        DaoUtil.TEAMS.put(20, away);
        try {
            Match byEspn = Match.builder()
                    .id(1)
                    .homeTeamId(10)
                    .awayTeamId(20)
                    .status("ns")
                    .espnId("espn-1")
                    .startsAt(LocalDateTime.of(2026, 11, 22, 15, 0))
                    .homeScore(null)
                    .awayScore(null)
                    .build();
            Match other = Match.builder()
                    .id(2)
                    .homeTeamId(10)
                    .awayTeamId(20)
                    .status("ft")
                    .espnId("espn-old")
                    .startsAt(LocalDateTime.of(2025, 1, 1, 15, 0))
                    .homeScore(1)
                    .awayScore(0)
                    .build();
            assertSame(byEspn, DataInitService.resolveUpcomingMatch(
                    List.of(other, byEspn), "espn-1", "AAA", "BBB"));
            Match byTeams = Match.builder()
                    .id(3)
                    .homeTeamId(10)
                    .awayTeamId(20)
                    .status("ns")
                    .startsAt(LocalDateTime.of(2026, 11, 23, 15, 0))
                    .build();
            assertSame(byTeams, DataInitService.resolveUpcomingMatch(
                    List.of(other, byTeams), null, "AAA", "BBB"));
            assertNull(DataInitService.resolveUpcomingMatch(
                    List.of(other), null, "AAA", "BBB"));
            assertEquals(1, byEspn.getId());
            assertEquals("espn-1", byEspn.getEspnId());
            assertNull(byEspn.getHomeScore());
        } finally {
            DaoUtil.TEAMS.remove(10);
            DaoUtil.TEAMS.remove(20);
        }
    }
}
