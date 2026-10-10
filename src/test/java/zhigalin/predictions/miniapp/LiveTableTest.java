package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamSeasonStats;
import zhigalin.predictions.model.event.Match;

class LiveTableTest {

    @Test
    void liveResultReflectsTeamPerspective() {
        Match match = Match.builder()
                .homeTeamId(1)
                .awayTeamId(2)
                .homeTeamScore(1)
                .awayTeamScore(0)
                .status("1h")
                .localDateTime(LocalDateTime.of(2026, 8, 29, 18, 0))
                .build();
        assertEquals("W", LiveTable.resultForTeam(match, 1));
        assertEquals("L", LiveTable.resultForTeam(match, 2));

        match.setHomeTeamScore(1);
        match.setAwayTeamScore(1);
        assertEquals("D", LiveTable.resultForTeam(match, 1));
        assertEquals("D", LiveTable.resultForTeam(match, 2));
    }

    @Test
    void liveScoreReflectsTeamPerspective() {
        Match match = Match.builder()
                .homeTeamId(10)
                .awayTeamId(20)
                .homeTeamScore(1)
                .awayTeamScore(0)
                .status("1h")
                .localDateTime(LocalDateTime.of(2026, 8, 29, 18, 0))
                .build();
        assertEquals("1:0", LiveTable.scoreForTeam(match, 10));
        assertEquals("0:1", LiveTable.scoreForTeam(match, 20));

        match.setHomeTeamId(30);
        match.setAwayTeamId(40);
        match.setHomeTeamScore(1);
        match.setAwayTeamScore(3);
        assertEquals("1:3", LiveTable.scoreForTeam(match, 30));
        assertEquals("3:1", LiveTable.scoreForTeam(match, 40));
    }

    private static Match live(int home, int away, String status, Integer hs, Integer as) {
        return Match.builder().homeTeamId(home).awayTeamId(away).status(status)
                .homeTeamScore(hs).awayTeamScore(as).build();
    }

    @Test
    void liveMatchByTeamKeepsOnlyScoredInPlayMatches() {
        Match playing = live(1, 2, "2h", 1, 0);
        Match noScore = live(3, 4, "1h", null, null);
        Match finished = live(5, 6, "ft", 2, 2);
        Match pending = live(7, 8, "ns", 0, 0);
        Map<Integer, Match> byTeam = LiveTable.liveMatchByTeam(List.of(playing, noScore, finished, pending));
        assertEquals(Set.of(1, 2), byTeam.keySet());
        assertSame(playing, byTeam.get(1));
        assertSame(playing, byTeam.get(2));
    }

    @Test
    void placeDeltaIsZeroWithoutLiveMatches() {
        assertEquals(0, LiveTable.placeDelta(false, 5, 2));
        assertEquals(3, LiveTable.placeDelta(true, 5, 2));
        assertEquals(-2, LiveTable.placeDelta(true, 2, 4));
    }

    @Test
    void seasonStatsEmptyOnlyWhenAllFieldsNull() {
        assertTrue(new TeamSeasonStats(null, null, null, null, null, null, null, null, null, null, null, null, null).isEmpty());
        assertFalse(new TeamSeasonStats(null, null, null, null, null, null, null, null, null, null, null, null, 3).isEmpty());
        assertFalse(new TeamSeasonStats(1.2, null, null, null, null, null, null, null, null, null, null, null, null).isEmpty());
    }
}

