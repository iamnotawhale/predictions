package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;

class MatchStatusTest {

    private static final LocalDateTime KICKOFF = LocalDateTime.of(2026, 8, 29, 18, 0);

    @Test
    void finishedStatusesAreCaseInsensitive() {
        for (String s : new String[] {"ft", "FT", "aet", "pen", "canc", "abd", "awrd", "wo"}) {
            assertTrue(MatchStatus.isFinished(s), s);
        }
        assertFalse(MatchStatus.isFinished("ns"));
        assertFalse(MatchStatus.isFinished("1h"));
        assertFalse(MatchStatus.isFinished(null));
    }

    @Test
    void notStartedTreatsMissingStatusAsNotStarted() {
        assertTrue(MatchStatus.isNotStarted(null));
        assertTrue(MatchStatus.isNotStarted(" "));
        assertTrue(MatchStatus.isNotStarted("NS"));
        assertTrue(MatchStatus.isNotStarted("pst"));
        assertFalse(MatchStatus.isNotStarted("1h"));
        assertFalse(MatchStatus.isNotStarted("ft"));
    }

    @Test
    void liveIsAnythingThatIsNeitherPendingNorClosed() {
        assertTrue(MatchStatus.isLive("1h"));
        assertTrue(MatchStatus.isLive("HT"));
        assertFalse(MatchStatus.isLive("ns"));
        assertFalse(MatchStatus.isLive("pst"));
        assertFalse(MatchStatus.isLive("ft"));
        assertFalse(MatchStatus.isLive(null));
        assertFalse(MatchStatus.isLive(""));
    }

    @Test
    void liveLikeAlsoCoversScoredMatchWithUnknownStatus() {
        Match scoredUnknown = Match.builder().status("tbd").homeTeamScore(1).awayTeamScore(0).build();
        assertTrue(MatchStatus.isLiveLike(scoredUnknown));

        Match finished = Match.builder().status("ft").homeTeamScore(1).awayTeamScore(0).build();
        assertFalse(MatchStatus.isLiveLike(finished));

        Match notStarted = Match.builder().status("ns").homeTeamScore(0).awayTeamScore(0).build();
        assertFalse(MatchStatus.isLiveLike(notStarted));

        assertFalse(MatchStatus.isLiveLike(null));
    }

    @Test
    void predictionWindowCloses5MinutesAfterKickoff() {
        assertTrue(MatchStatus.canPredictAt("ns", KICKOFF, KICKOFF.minusHours(3)));
        assertTrue(MatchStatus.canPredictAt("ns", KICKOFF, KICKOFF.plusMinutes(4)));
        assertFalse(MatchStatus.canPredictAt("ns", KICKOFF, KICKOFF.plusMinutes(5)));
        assertFalse(MatchStatus.canPredictAt("ft", KICKOFF, KICKOFF.minusDays(1)));
        assertFalse(MatchStatus.canPredictAt("ns", null, KICKOFF));
    }

    @Test
    void canPredictRejectsNull() {
        assertFalse(MatchStatus.canPredict(null));
        assertFalse(MatchStatus.canPredictBonus(null));
        assertEquals(false, MatchStatus.canPredict(Match.builder().status("ns").build()));
    }
}
