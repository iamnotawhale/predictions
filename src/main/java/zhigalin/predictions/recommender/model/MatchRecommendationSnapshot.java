package zhigalin.predictions.recommender.model;

import java.time.Instant;
import java.util.List;

public record MatchRecommendationSnapshot(
        int matchPublicId,
        int weekId,
        int recommendedHome,
        int recommendedAway,
        double expectedHomeGoals,
        double expectedAwayGoals,
        double scoreProbability,
        List<String> explanationLines,
        String summary,
        Instant computedAt
) {
}
