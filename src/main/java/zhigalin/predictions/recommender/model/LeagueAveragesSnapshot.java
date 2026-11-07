package zhigalin.predictions.recommender.model;

import java.time.Instant;

public record LeagueAveragesSnapshot(
        int weekId,
        double avgHomeScored,
        double avgAwayScored,
        double avgHomeConceded,
        double avgAwayConceded,
        Instant fetchedAt
) {
}
