package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;

class LiveStandingsResultTest {

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
        assertEquals("W", MiniAppService.liveResultForTeam(match, 1));
        assertEquals("L", MiniAppService.liveResultForTeam(match, 2));

        match.setHomeTeamScore(1);
        match.setAwayTeamScore(1);
        assertEquals("D", MiniAppService.liveResultForTeam(match, 1));
        assertEquals("D", MiniAppService.liveResultForTeam(match, 2));
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
        assertEquals("1-0", MiniAppService.liveScoreForTeam(match, 10));
        assertEquals("0-1", MiniAppService.liveScoreForTeam(match, 20));

        match.setHomeTeamId(30);
        match.setAwayTeamId(40);
        match.setHomeTeamScore(1);
        match.setAwayTeamScore(3);
        assertEquals("1-3", MiniAppService.liveScoreForTeam(match, 30));
        assertEquals("3-1", MiniAppService.liveScoreForTeam(match, 40));
    }
}
