package zhigalin.predictions.recommender.model;

import java.time.Instant;

public record FootyStatsTeamSnapshot(
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
        FootyStatsExtendedMetrics extended,
        Instant fetchedAt
) {
    public FootyStatsExtendedMetrics extendedOrEmpty() {
        return extended != null ? extended : FootyStatsExtendedMetrics.empty();
    }

    public FootyStatsTeamSnapshot withExtended(FootyStatsExtendedMetrics patch) {
        return new FootyStatsTeamSnapshot(
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

    public FootyStatsTeamSnapshot withXg(Double xg, Double xga, Double xgd, Double xgVsActual) {
        return new FootyStatsTeamSnapshot(
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
                        FootyStatsExtendedMetrics.builder().xgVsActual(xgVsActual).build()
                ),
                fetchedAt
        );
    }
}
