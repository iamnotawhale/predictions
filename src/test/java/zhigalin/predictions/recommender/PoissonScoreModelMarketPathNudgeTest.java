package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.LeagueAveragesSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

class PoissonScoreModelMarketPathNudgeTest {

    private static final LeagueAveragesSnapshot LEAGUE =
            new LeagueAveragesSnapshot(10, 1.55, 1.30, 1.30, 1.55, Instant.now());
    private static final double ODD_H = 2.10;
    private static final double ODD_D = 3.40;
    private static final double ODD_A = 3.50;

    @Test
    void emptyExtendedMatchesNeutralMarketShape() {
        var empty = run(ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty());
        var alsoEmpty = run(ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty());
        assertEquals(empty.lambdaHome(), alsoEmpty.lambdaHome(), 1e-12);
        assertEquals(empty.lambdaAway(), alsoEmpty.lambdaAway(), 1e-12);
        assertEquals(empty.recommendedHome(), alsoEmpty.recommendedHome());
        assertEquals(empty.recommendedAway(), alsoEmpty.recommendedAway());
    }

    @Test
    void xPtsAndSoccerStatsNudgeKeepsMarket1x2AndMovesACell() {
        ExtendedTeamMetrics extreme = ExtendedTeamMetrics.builder()
                .xPts(20.0, 10.0, -8.0)
                .ssScoredFirst(85.0, 2.6)
                .ssLeadDurations(70.0, 15.0)
                .ssFavouritePpg(2.8)
                .ssFirstGoal(70.0, 30.0, 18.0)
                .build();
        var baseline = run(ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty());
        var nudged = run(extreme, extreme);

        assertEquals(baseline.distribution().homeWin(), nudged.distribution().homeWin(), 1e-9);
        assertEquals(baseline.distribution().draw(), nudged.distribution().draw(), 1e-9);
        assertEquals(baseline.distribution().awayWin(), nudged.distribution().awayWin(), 1e-9);

        boolean cellMoved = false;
        var baseM = baseline.distribution().matrix();
        var nudgeM = nudged.distribution().matrix();
        for (int h = 0; h < baseM.size(); h++) {
            for (int a = 0; a < baseM.get(h).size(); a++) {
                if (Math.abs(baseM.get(h).get(a) - nudgeM.get(h).get(a)) > 1e-12) {
                    cellMoved = true;
                    break;
                }
            }
        }
        assertTrue(cellMoved || Math.abs(baseline.lambdaHome() - nudged.lambdaHome()) > 1e-9
                        || Math.abs(baseline.lambdaAway() - nudged.lambdaAway()) > 1e-9,
                "nudge should move λ or at least one matrix cell");
    }

    @Test
    void mixRecentXgIgnoresThinWindowAndNullSeason() {
        assertEquals(null, PoissonScoreModel.mixRecentXg(null, 2.0, 6));
        assertEquals(1.4, PoissonScoreModel.mixRecentXg(1.4, 2.0, 3), 1e-12);
        double mixed = PoissonScoreModel.mixRecentXg(1.0, 2.0, 6);
        assertEquals(1.0 * 0.45 + 2.0 * 0.55, mixed, 1e-12);
        double partial = PoissonScoreModel.mixRecentXg(1.0, 2.0, 4);
        assertTrue(partial > 1.0 && partial < mixed);
    }

    @Test
    void analysisIsPresentOnMarketPath() {
        var r = run(ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty());
        assertTrue(r.explanation().analysis() != null && !r.explanation().analysis().isBlank());
        assertTrue(r.explanation().analysis().contains("рекомендуем"));
        assertTrue(r.explanation().analysis().contains("call"));
    }

    @Test
    void recentXgOnlyBlendsWhenSeasonReady() {
        TeamStatsSnapshot noSeasonXg = new TeamStatsSnapshot(
                "HOM", 1.5, 1.6, 1.4, 1.2, 1.1, 1.3,
                null, null, null, null, null,
                ExtendedTeamMetrics.builder().recentXg(2.4, 0.8, 6).build(),
                Instant.now()
        );
        TeamStatsSnapshot away = team("AWY", ExtendedTeamMetrics.empty());
        var withRecentOnly = PoissonScoreModel.recommend(
                ODD_H, ODD_D, ODD_A, noSeasonXg, away, LEAGUE, null, 2.5, null, null
        );
        TeamStatsSnapshot noRecent = new TeamStatsSnapshot(
                "HOM", 1.5, 1.6, 1.4, 1.2, 1.1, 1.3,
                null, null, null, null, null,
                ExtendedTeamMetrics.empty(),
                Instant.now()
        );
        var baseline = PoissonScoreModel.recommend(
                ODD_H, ODD_D, ODD_A, noRecent, away, LEAGUE, null, 2.5, null, null
        );
        assertEquals(baseline.lambdaHome(), withRecentOnly.lambdaHome(), 1e-12);
        assertEquals(baseline.lambdaAway(), withRecentOnly.lambdaAway(), 1e-12);
    }

    @Test
    void recentXgMovesLambdaWhenSeasonXgPresent() {
        ExtendedTeamMetrics recentHigh = ExtendedTeamMetrics.builder()
                .recentXg(2.8, 0.7, 6)
                .build();
        ExtendedTeamMetrics recentLow = ExtendedTeamMetrics.builder()
                .recentXg(0.6, 2.0, 6)
                .build();
        TeamStatsSnapshot high = seasonXgTeam("HOM", 1.4, 1.3, recentHigh);
        TeamStatsSnapshot low = seasonXgTeam("HOM", 1.4, 1.3, recentLow);
        TeamStatsSnapshot away = seasonXgTeam("AWY", 1.3, 1.4, ExtendedTeamMetrics.empty());
        var a = PoissonScoreModel.recommend(ODD_H, ODD_D, ODD_A, high, away, LEAGUE, null, 2.5, null, null);
        var b = PoissonScoreModel.recommend(ODD_H, ODD_D, ODD_A, low, away, LEAGUE, null, 2.5, null, null);
        assertEquals(a.distribution().homeWin(), b.distribution().homeWin(), 1e-9);
        assertNotEquals(a.lambdaHome(), b.lambdaHome(), 1e-9);
    }

    private static PoissonScoreModel.Result run(ExtendedTeamMetrics homeExt, ExtendedTeamMetrics awayExt) {
        return PoissonScoreModel.recommend(
                ODD_H, ODD_D, ODD_A,
                team("HOM", homeExt),
                team("AWY", awayExt),
                LEAGUE,
                null,
                2.5,
                null,
                null
        );
    }

    private static TeamStatsSnapshot team(String code, ExtendedTeamMetrics ext) {
        return new TeamStatsSnapshot(
                code, 1.5, 1.6, 1.4, 1.2, 1.1, 1.3,
                null, null, null, null, null,
                ext,
                Instant.now()
        );
    }

    private static TeamStatsSnapshot seasonXgTeam(
            String code, double xg, double xga, ExtendedTeamMetrics ext
    ) {
        return new TeamStatsSnapshot(
                code, 1.5, 1.6, 1.4, 1.2, 1.1, 1.3,
                xg, xga, xg - xga, xg, xg,
                ext,
                Instant.now()
        );
    }
}
