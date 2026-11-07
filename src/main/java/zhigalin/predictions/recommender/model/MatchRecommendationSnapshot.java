package zhigalin.predictions.recommender.model;

import java.time.Instant;
import java.util.List;

public record MatchRecommendationSnapshot(
        int matchId,
        int weekId,
        int recommendedHome,
        int recommendedAway,
        double expectedHome,
        double expectedAway,
        double scoreProbability,
        ExplanationBreakdown explanation,
        String summary,
        Instant computedAt,
        Integer kickoffHome,
        Integer kickoffAway,
        Instant kickoffFrozenAt,
        Integer shadowHome,
        Integer shadowAway
) {
    public MatchRecommendationSnapshot(
            int matchId,
            int weekId,
            int recommendedHome,
            int recommendedAway,
            double expectedHome,
            double expectedAway,
            double scoreProbability,
            ExplanationBreakdown explanation,
            String summary,
            Instant computedAt,
            Integer kickoffHome,
            Integer kickoffAway,
            Instant kickoffFrozenAt
    ) {
        this(matchId, weekId, recommendedHome, recommendedAway, expectedHome, expectedAway,
                scoreProbability, explanation, summary, computedAt, kickoffHome, kickoffAway, kickoffFrozenAt,
                null, null);
    }

    public MatchRecommendationSnapshot(
            int matchId,
            int weekId,
            int recommendedHome,
            int recommendedAway,
            double expectedHome,
            double expectedAway,
            double scoreProbability,
            ExplanationBreakdown explanation,
            String summary,
            Instant computedAt
    ) {
        this(
                matchId,
                weekId,
                recommendedHome,
                recommendedAway,
                expectedHome,
                expectedAway,
                scoreProbability,
                explanation,
                summary,
                computedAt,
                null,
                null,
                null
        );
    }

    public MatchRecommendationSnapshot(
            int matchId,
            int weekId,
            int recommendedHome,
            int recommendedAway,
            double expectedHome,
            double expectedAway,
            double scoreProbability,
            List<String> explanationLines,
            String summary,
            Instant computedAt
    ) {
        this(
                matchId,
                weekId,
                recommendedHome,
                recommendedAway,
                expectedHome,
                expectedAway,
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
