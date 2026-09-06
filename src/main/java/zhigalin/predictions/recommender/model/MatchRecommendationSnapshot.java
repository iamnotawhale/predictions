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
        Instant computedAt,
        Integer kickoffHome,
        Integer kickoffAway,
        Instant kickoffFrozenAt
) {
    public MatchRecommendationSnapshot(
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
        this(
                matchPublicId,
                weekId,
                recommendedHome,
                recommendedAway,
                expectedHomeGoals,
                expectedAwayGoals,
                scoreProbability,
                explanationLines,
                summary,
                computedAt,
                null,
                null,
                null
        );
    }

    public boolean hasKickoffFreeze() {
        return kickoffFrozenAt != null && kickoffHome != null && kickoffAway != null;
    }
}
