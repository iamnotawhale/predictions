package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

class PoissonScoreModelThinSampleTest {

    @Test
    void earlySeasonZeroBttsDoesNotForceOneNilEverywhere() {
        FootyStatsLeagueSnapshot league =
                new FootyStatsLeagueSnapshot(2, 1.45, 1.20, 1.20, 1.45, Instant.now());
        // No venue goals yet — FootyStats often still reports 0% BTTS/CS.
        FootyStatsExtendedMetrics zeros = FootyStatsExtendedMetrics.builder()
                .formBtts(0.0, 0.0, 0.0)
                .formCs(0.0, 0.0, 0.0)
                .seasonBtts(0.0, 0.0, 0.0)
                .over25(0.0, 0.0, 0.0)
                .under25(0.0, 0.0, 0.0)
                .build();

        Set<String> tips = new HashSet<>();
        double[][] odds = {
                {1.40, 4.50, 8.00}, // strong home
                {2.20, 3.40, 3.20}, // slight home
                {3.40, 3.40, 2.15}, // slight away
                {7.50, 4.80, 1.45}  // strong away
        };
        for (double[] o : odds) {
            FootyStatsTeamSnapshot home = team("HOM", zeros);
            FootyStatsTeamSnapshot away = team("AWY", zeros);
            PoissonScoreModel.Result result = PoissonScoreModel.recommend(
                    o[0], o[1], o[2], home, away, league, null
            );
            tips.add(result.recommendedHome() + ":" + result.recommendedAway());
        }
        assertFalse(tips.size() == 1 && tips.contains("1:0"),
                "tips collapsed to only 1:0: " + tips);
        assertNotEquals(Set.of("1:0"), tips);
    }

    private static FootyStatsTeamSnapshot team(String code, FootyStatsExtendedMetrics ext) {
        return new FootyStatsTeamSnapshot(
                code,
                0, 0, 0,
                0, 0, 0,
                0.3, 1.4, -1.1, 0.3, 0.3,
                ext,
                Instant.now()
        );
    }
}
