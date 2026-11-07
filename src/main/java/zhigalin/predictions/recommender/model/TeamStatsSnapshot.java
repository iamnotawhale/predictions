package zhigalin.predictions.recommender.model;

import java.time.Instant;

public record TeamStatsSnapshot(
        String teamCode,
        double scoredOverall,
        double scoredHome,
        double scoredAway,
        double concededOverall,
        double concededHome,
        double concededAway,
        Double xgOverall,
        Double xgaOverall,
        Double xgdOverall,
        Double xgHome,
        Double xgAway,
        ExtendedTeamMetrics extended,
        Instant fetchedAt
) {
    public ExtendedTeamMetrics extendedOrEmpty() {
        return extended != null ? extended : ExtendedTeamMetrics.empty();
    }

    public TeamStatsSnapshot withExtended(ExtendedTeamMetrics patch) {
        return new TeamStatsSnapshot(
                teamCode,
                scoredOverall,
                scoredHome,
                scoredAway,
                concededOverall,
                concededHome,
                concededAway,
                xgOverall,
                xgaOverall,
                xgdOverall,
                xgHome,
                xgAway,
                extendedOrEmpty().merge(patch),
                fetchedAt
        );
    }

    public TeamStatsSnapshot withXg(Double xg, Double xga, Double xgd, Double xgVsActual) {
        return new TeamStatsSnapshot(
                teamCode,
                scoredOverall,
                scoredHome,
                scoredAway,
                concededOverall,
                concededHome,
                concededAway,
                xg,
                xga,
                xgd,
                xgHome,
                xgAway,
                extendedOrEmpty().merge(
                        ExtendedTeamMetrics.builder().xgVsActual(xgVsActual).build()
                ),
                fetchedAt
        );
    }
}
