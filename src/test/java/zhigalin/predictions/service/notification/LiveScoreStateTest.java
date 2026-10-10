package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;

class LiveScoreStateTest {

    private final LiveScoreState state = new LiveScoreState();
    private final Team home = Team.builder().code("MCI").build();
    private final Team away = Team.builder().code("ARS").build();

    private static Match.MatchBuilder match() {
        return Match.builder().homeTeamId(1).awayTeamId(2);
    }

    @Test
    void keyPrefersEspnThenPublicThenTeams() {
        assertEquals("espn:99", state.key(match().espnId("99").publicId(5).build(), home, away));
        assertEquals("pub:5", state.key(match().publicId(5).build(), home, away));
        assertEquals("pub:5", state.key(match().espnId(" ").publicId(5).build(), home, away));
        assertEquals("teams:MCI-ARS", state.key(match().build(), home, away));
        assertEquals("teams:1-2", state.key(match().build(), null, null));
    }

    @Test
    void existingMessageIdIsFoundViaAnyAliasAndCachedUnderPrimaryKey() {
        Match m = match().espnId("99").publicId(5).build();
        state.putMessageId("pub:5", 111);
        assertEquals(111, state.existingMessageId(m, "espn:99", home, away));
        state.removeMessageId("pub:5");
        assertEquals(111, state.existingMessageId(m, "espn:99", home, away), "cached under primary key");
    }

    @Test
    void persistedMessageIdWinsOverAliases() {
        Match m = match().publicId(5).liveScoreMessageId(777).build();
        state.putMessageId("pub:5", 111);
        assertEquals(777, state.existingMessageId(m, "espn:x", home, away));
        assertEquals(777, state.existingMessageId(m, "espn:x", home, away));
    }

    @Test
    void teamsAliasIsUsedLast() {
        Match m = match().publicId(5).build();
        state.putMessageId("teams:MCI-ARS", 42);
        assertEquals(42, state.existingMessageId(m, "pub:5", home, away));
    }

    @Test
    void unknownMatchHasNoMessage() {
        assertNull(state.existingMessageId(match().publicId(8).build(), "pub:8", home, away));
    }

    @Test
    void clearForgetsPrimaryAndAllAliases() {
        Match m = match().espnId("99").publicId(5).build();
        for (String k : new String[] {"espn:99", "pub:5", "teams:MCI-ARS"}) {
            state.putMessageId(k, 1);
            state.putLastText(k, "t");
            state.markAnimation(k);
        }
        state.clear(m, home, away);
        for (String k : new String[] {"espn:99", "pub:5", "teams:MCI-ARS"}) {
            assertNull(state.lastText(k), k);
            assertFalse(state.isAnimation(k), k);
        }
        assertNull(state.existingMessageId(m, "espn:99", home, away));
    }

    @Test
    void clearLeavesOtherMatchesAlone() {
        state.putMessageId("pub:6", 9);
        state.putLastText("pub:6", "x");
        state.clear(match().publicId(5).build(), home, away);
        assertEquals("x", state.lastText("pub:6"));
        assertTrue(state.existingMessageId(match().publicId(6).build(), "pub:6", null, null) == 9);
    }
}
