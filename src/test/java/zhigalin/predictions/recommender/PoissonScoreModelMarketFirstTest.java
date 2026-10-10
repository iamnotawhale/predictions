package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

class PoissonScoreModelMarketFirstTest {

    private static final FootyStatsLeagueSnapshot LEAGUE =
            new FootyStatsLeagueSnapshot(8, 1.60, 1.35, 1.35, 1.60, Instant.now());

    private static FootyStatsTeamSnapshot team(String code, double sh, double ch, double sa, double ca) {
        return new FootyStatsTeamSnapshot(code, (sh + sa) / 2, sh, sa, (ch + ca) / 2, ch, ca,
                null, null, null, null, null, FootyStatsExtendedMetrics.empty(), Instant.now());
    }

    private static PoissonScoreModel.Result run(double h, double d, double a,
                                                FootyStatsTeamSnapshot home, FootyStatsTeamSnapshot away,
                                                Double line) {
        return PoissonScoreModel.recommend(h, d, a, home, away, LEAGUE, null, line, null, null);
    }

    private static double[] market(double h, double d, double a) {
        double s = 1 / h + 1 / d + 1 / a;
        return new double[] {1 / h / s, 1 / d / s, 1 / a / s};
    }

    @Test
    void outcomeProbabilitiesEqualTheDeViggedMarket() {
        var r = run(1.60, 4.10, 5.50, team("H", 1.8, 1.0, 1.5, 1.2), team("A", 1.1, 1.5, 0.9, 1.7), 2.5);
        double[] m = market(1.60, 4.10, 5.50);
        assertEquals(m[0], r.distribution().homeWin(), 1e-6);
        assertEquals(m[1], r.distribution().draw(), 1e-6);
        assertEquals(m[2], r.distribution().awayWin(), 1e-6);
    }

    @Test
    void teamStatsDoNotChangeWhoIsLikelyToWin() {
        var strongStats = run(2.40, 3.30, 3.00, team("H", 2.6, 0.6, 2.2, 0.8), team("A", 0.7, 2.0, 0.5, 2.2), 2.5);
        var weakStats = run(2.40, 3.30, 3.00, team("H", 0.7, 2.0, 0.5, 2.2), team("A", 2.6, 0.6, 2.2, 0.8), 2.5);
        assertEquals(strongStats.distribution().homeWin(), weakStats.distribution().homeWin(), 1e-6);
        assertEquals(strongStats.distribution().awayWin(), weakStats.distribution().awayWin(), 1e-6);
    }

    @Test
    void recommendedScoreMaximisesExpectedPointsOverTheShownMatrix() {
        var r = run(2.10, 3.50, 3.60, team("H", 1.5, 1.2, 1.3, 1.3), team("A", 1.4, 1.3, 1.2, 1.4), 2.5);
        var matrix = r.distribution().matrix();
        double best = -9;
        for (int h = 0; h < matrix.size(); h++) {
            for (int a = 0; a < matrix.size(); a++) {
                double ev = 0;
                for (int i = 0; i < matrix.size(); i++) {
                    for (int j = 0; j < matrix.size(); j++) {
                        ev += matrix.get(i).get(j) * PredictionService.computePoints(ScoringMode.EPL, i, j, h, a);
                    }
                }
                best = Math.max(best, ev);
            }
        }
        assertEquals(best, r.expectedPoints(), 1e-9);
    }

    @Test
    void evenAndLopsidedMatchesGetSensibleWinnerTips() {
        var even = run(2.65, 3.30, 2.80, team("H", 1.5, 1.3, 1.3, 1.4), team("A", 1.5, 1.3, 1.3, 1.4), 2.5);
        // a very even match may legitimately get a draw tip, but never a lopsided or high-scoring one
        assertTrue(Math.abs(even.recommendedHome() - even.recommendedAway()) <= 1);
        assertTrue(even.recommendedHome() + even.recommendedAway() <= 3);
        var favourite = run(1.30, 5.50, 10.0, team("H", 2.2, 0.8, 1.9, 1.0), team("A", 0.9, 1.9, 0.8, 2.1), 2.5);
        assertTrue(favourite.recommendedHome() > favourite.recommendedAway());
        var underdog = run(8.50, 5.00, 1.35, team("H", 0.9, 1.9, 0.8, 2.1), team("A", 2.2, 0.8, 1.9, 1.0), 2.5);
        assertTrue(underdog.recommendedAway() > underdog.recommendedHome());
    }

    @Test
    void higherTotalLineRaisesExpectedGoals() {
        var low = run(2.20, 3.40, 3.30, team("H", 1.5, 1.3, 1.3, 1.4), team("A", 1.5, 1.3, 1.3, 1.4), 1.5);
        var high = run(2.20, 3.40, 3.30, team("H", 1.5, 1.3, 1.3, 1.4), team("A", 1.5, 1.3, 1.3, 1.4), 3.5);
        assertTrue(high.lambdaHome() + high.lambdaAway() > low.lambdaHome() + low.lambdaAway() + 0.4);
    }

    @Test
    void explanationSaysTheForecastIsMarketBased() {
        var r = run(2.20, 3.40, 3.30, team("H", 1.5, 1.3, 1.3, 1.4), team("A", 1.5, 1.3, 1.3, 1.4), 2.5);
        List<String> lines = r.explanationLines();
        assertTrue(lines.stream().anyMatch(l -> l.contains("коэффициенты букмекеров")));
    }

    @Test
    void withoutOddsTheStatsOnlyFallbackStillWorks() {
        var r = PoissonScoreModel.recommend(null, null, null,
                team("H", 1.8, 1.0, 1.5, 1.2), team("A", 1.1, 1.5, 0.9, 1.7), LEAGUE, null, null, null, null);
        assertTrue(r.distribution().hasMatrix());
        assertEquals(1.0, r.distribution().homeWin() + r.distribution().draw() + r.distribution().awayWin(), 1e-6);
        assertTrue(r.explanationLines().stream().noneMatch(l -> l.contains("коэффициенты букмекеров; статистика")));
    }
}
