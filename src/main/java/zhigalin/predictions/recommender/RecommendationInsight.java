package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

/**
 * Mini App view of a recommendation: for every possible pick the chance of each payout tier and the
 * short "why" reasons. Everything is derived from the stored score matrix and
 * {@link PredictionService#computePoints}, so the rules stay defined in one place.
 */
public final class RecommendationInsight {

    /** Points for exact score / same goal difference / same outcome / miss. */
    public record Scoring(int exact, int diff, int outcome, int miss) {
    }

    /** Chance of each payout tier if this score is picked (they sum to 1). */
    public record Tiers(double exact, double diff, double outcome, double miss) {
        public double pointsChance() {
            return 1.0 - miss;
        }
    }

    public record ModeScore(int home, int away, double probability, double expectedPoints) {
    }

    public record Insight(
            Scoring scoring,
            List<List<List<Double>>> breakdown,
            double expectedPoints,
            double pointsChance,
            ModeScore mostLikely,
            List<String> reasons
    ) {
    }

    private RecommendationInsight() {
    }

    public static Insight build(
            double[][] matrix,
            int pickHome,
            int pickAway,
            double expectedHome,
            double expectedAway,
            boolean marketBased
    ) {
        Scoring scoring = new Scoring(
                points(1, 0, 1, 0), points(2, 1, 1, 0), points(2, 0, 1, 0), points(0, 1, 1, 0));
        int n = matrix.length;
        List<List<List<Double>>> breakdown = new ArrayList<>();
        for (int h = 0; h < n; h++) {
            List<List<Double>> row = new ArrayList<>();
            for (int a = 0; a < matrix[h].length; a++) {
                Tiers t = tiers(matrix, h, a);
                row.add(List.of(t.exact(), t.diff(), t.outcome(), t.miss()));
            }
            breakdown.add(row);
        }
        Tiers pick = tiers(matrix, pickHome, pickAway);
        double pickEv = expected(pick, scoring);
        int mh = 0;
        int ma = 0;
        for (int h = 0; h < n; h++) {
            for (int a = 0; a < matrix[h].length; a++) {
                if (matrix[h][a] > matrix[mh][ma]) {
                    mh = h;
                    ma = a;
                }
            }
        }
        double modeEv = expected(tiers(matrix, mh, ma), scoring);
        ModeScore mode = new ModeScore(mh, ma, matrix[mh][ma], modeEv);
        return new Insight(
                scoring, breakdown, pickEv, pick.pointsChance(), mode,
                reasons(matrix, pickHome, pickAway, pick, pickEv, mode, expectedHome, expectedAway, marketBased));
    }

    static Tiers tiers(double[][] matrix, int ph, int pa) {
        double exact = 0;
        double diff = 0;
        double outcome = 0;
        double miss = 0;
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix[h].length; a++) {
                double p = matrix[h][a];
                int pts = PredictionService.computePoints(ScoringMode.EPL, h, a, ph, pa);
                if (pts >= 4) {
                    exact += p;
                } else if (pts >= 2) {
                    diff += p;
                } else if (pts >= 1) {
                    outcome += p;
                } else {
                    miss += p;
                }
            }
        }
        return new Tiers(exact, diff, outcome, miss);
    }

    private static int points(int realH, int realA, int predH, int predA) {
        return PredictionService.computePoints(ScoringMode.EPL, realH, realA, predH, predA);
    }

    private static double expected(Tiers t, Scoring s) {
        return t.exact() * s.exact() + t.diff() * s.diff() + t.outcome() * s.outcome() + t.miss() * s.miss();
    }

    private static List<String> reasons(
            double[][] m, int ph, int pa, Tiers pick, double pickEv, ModeScore mode,
            double expectedHome, double expectedAway, boolean marketBased
    ) {
        double home = 0;
        double draw = 0;
        double away = 0;
        double over25 = 0;
        for (int h = 0; h < m.length; h++) {
            for (int a = 0; a < m[h].length; a++) {
                if (h > a) {
                    home += m[h][a];
                } else if (h == a) {
                    draw += m[h][a];
                } else {
                    away += m[h][a];
                }
                if (h + a >= 3) {
                    over25 += m[h][a];
                }
            }
        }
        List<String> out = new ArrayList<>();
        if (marketBased) {
            out.add(String.format(Locale.US, "Букмекеры: победа хозяев %s, ничья %s, победа гостей %s.",
                    pct(home), pct(draw), pct(away)));
        } else {
            out.add(String.format(Locale.US,
                    "Коэффициентов пока нет — прогноз только по статистике (хозяева %s, ничья %s, гости %s). Он менее надёжен.",
                    pct(home), pct(draw), pct(away)));
        }
        out.add(String.format(Locale.US, "Ждём около %.1f : %.1f гола, тотал больше 2.5 — %s.",
                expectedHome, expectedAway, pct(over25)));
        if (mode.home() == ph && mode.away() == pa) {
            out.add(String.format(Locale.US,
                    "%d:%d — самый вероятный счёт (%s) и он же приносит больше всего очков.",
                    ph, pa, pct(pick.exact())));
        } else {
            out.add(String.format(Locale.US,
                    "Самый вероятный счёт %d:%d (%s), но %d:%d выгоднее: в среднем %.2f очка против %.2f — "
                            + "чаще попадает хотя бы в исход или разницу голов.",
                    mode.home(), mode.away(), pct(mode.probability()), ph, pa, pickEv, mode.expectedPoints()));
        }
        out.add(String.format(Locale.US, "Очки получим с шансом %s, точный счёт — %s.",
                pct(pick.pointsChance()), pct(pick.exact())));
        return out;
    }

    private static String pct(double p) {
        return String.format(Locale.US, "%.0f%%", p * 100);
    }
}
