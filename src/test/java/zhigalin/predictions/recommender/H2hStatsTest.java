package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.HeadToHead;

class H2hStatsTest {

    @Test
    void aggregatesOverallAndSameVenueFromCurrentHomePerspective() {
        // TOT=10 home vs NEW=20: TOT won 2-1 at home
        // NEW home vs TOT: NEW won 3-0 (TOT away loss)
        // TOT home vs NEW: draw 1-1
        List<HeadToHead> meetings = List.of(
                meeting(10, 20, 2, 1),
                meeting(20, 10, 3, 0),
                meeting(10, 20, 1, 1)
        );

        H2hStats stats = H2hStats.from(meetings, 10, 20);
        assertNotNull(stats);
        assertEquals(3, stats.overallGames());
        assertEquals(1, stats.currentHomeWinsOverall());
        assertEquals(1, stats.drawsOverall());
        assertEquals(1, stats.currentAwayWinsOverall());
        assertEquals(1.0, stats.avgGoalsCurrentHomeOverall(), 0.01); // (2+0+1)/3
        assertEquals(5.0 / 3.0, stats.avgGoalsCurrentAwayOverall(), 0.01); // (1+3+1)/3

        assertEquals(2, stats.venueGames());
        assertEquals(1, stats.currentHomeWinsAtVenue());
        assertEquals(1, stats.drawsAtVenue());
        assertEquals(0, stats.currentAwayWinsAtVenue());
        assertEquals(1.5, stats.avgGoalsCurrentHomeAtVenue(), 0.01); // (2+1)/2
        assertEquals(1.0, stats.avgGoalsCurrentAwayAtVenue(), 0.01); // (1+1)/2
    }

    @Test
    void h2hLambdaPullsTowardVenueGoals() {
        H2hStats stats = new H2hStats(
                6, 4, 1, 1, 2.0, 0.8,
                4, 3, 1, 0, 2.2, 0.7
        );
        double nudged = PoissonScoreModel.applyH2hLambda(1.0, stats, true);
        assertTrue(nudged > 1.0);
        assertTrue(nudged < 2.2);
    }

    @Test
    void emptyMeetingsYieldNull() {
        assertNull(H2hStats.from(List.of(), 1, 2));
    }

    private static HeadToHead meeting(int homeId, int awayId, int homeScore, int awayScore) {
        return HeadToHead.builder()
                .homeTeamId(homeId)
                .awayTeamId(awayId)
                .homeTeamScore(homeScore)
                .awayTeamScore(awayScore)
                .localDateTime(LocalDateTime.of(2025, 1, 1, 15, 0))
                .leagueName("PL")
                .build();
    }
}
