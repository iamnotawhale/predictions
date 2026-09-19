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
        ExplanationBreakdown explanation,
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
            ExplanationBreakdown explanation,
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
                explanation,
                summary,
                computedAt,
                null,
                null,
                null
        );
    }

    /** Legacy constructor: flat explanation lines become notes. */
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
                new ExplanationBreakdown(List.of(), explanationLines != null ? explanationLines : List.of()),
                summary,
                computedAt,
                null,
                null,
                null
        );
    }

    public List<String> explanationLines() {
        return explanation != null ? explanation.asLines() : List.of();
    }

    public boolean hasKickoffFreeze() {
        return kickoffFrozenAt != null && kickoffHome != null && kickoffAway != null;
    }
}
