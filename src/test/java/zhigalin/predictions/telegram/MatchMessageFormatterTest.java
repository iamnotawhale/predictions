package zhigalin.predictions.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;

class MatchMessageFormatterTest {

    @BeforeEach
    void setUpTeams() {
        DaoUtil.TEAMS.clear();
        DaoUtil.TEAMS.put(1, Team.builder().publicId(1).code("ARS").name("Arsenal").build());
        DaoUtil.TEAMS.put(2, Team.builder().publicId(2).code("CHE").name("Chelsea").build());
    }

    @AfterEach
    void tearDown() {
        DaoUtil.TEAMS.clear();
    }

    @Test
    void todayFinishedShowsScoreAndStatus() {
        Match match = Match.builder()
                .homeTeamId(1)
                .awayTeamId(2)
                .homeTeamScore(2)
                .awayTeamScore(1)
                .status("ft")
                .localDateTime(LocalDateTime.of(2026, 8, 29, 17, 0))
                .build();
        StringBuilder sb = new StringBuilder();
        assertTrue(MatchMessageFormatter.appendMatchBody(sb, match, MatchMessageFormatter.Style.TODAY));
        assertEquals("ARS 2 - 1 CHE ft ", sb.toString());
    }

    @Test
    void todayNotStartedShowsKickoffTime() {
        Match match = Match.builder()
                .homeTeamId(1)
                .awayTeamId(2)
                .status("ns")
                .localDateTime(LocalDateTime.of(2026, 8, 29, 17, 30))
                .build();
        StringBuilder sb = new StringBuilder();
        assertTrue(MatchMessageFormatter.appendMatchBody(sb, match, MatchMessageFormatter.Style.TODAY));
        assertEquals("ARS - CHE ⏱ 17:30", sb.toString());
    }

    @Test
    void tourFinishedShowsScoreWithoutStatus() {
        Match match = Match.builder()
                .homeTeamId(1)
                .awayTeamId(2)
                .homeTeamScore(0)
                .awayTeamScore(0)
                .status("ft")
                .build();
        StringBuilder sb = new StringBuilder();
        assertTrue(MatchMessageFormatter.appendMatchBody(sb, match, MatchMessageFormatter.Style.TOUR));
        assertEquals("ARS 0 - 0 CHE", sb.toString());
    }

    @Test
    void upcomingPostponedShowsClockEmoji() {
        Match match = Match.builder()
                .homeTeamId(1)
                .awayTeamId(2)
                .status("pst")
                .localDateTime(LocalDateTime.of(2026, 8, 30, 15, 0))
                .build();
        StringBuilder sb = new StringBuilder();
        assertTrue(MatchMessageFormatter.appendMatchBody(sb, match, MatchMessageFormatter.Style.UPCOMING));
        assertEquals("ARS - CHE ⏰ pst", sb.toString());
    }

    @Test
    void returnsFalseWhenTeamMissing() {
        Match match = Match.builder().homeTeamId(1).awayTeamId(99).status("ns").build();
        assertFalse(MatchMessageFormatter.appendMatchBody(new StringBuilder(), match, MatchMessageFormatter.Style.TODAY));
    }
}
