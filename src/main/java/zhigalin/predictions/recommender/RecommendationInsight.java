package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
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
        return build(matrix, pickHome, pickAway, expectedHome, expectedAway, marketBased,
                ExtendedTeamMetrics.empty(), ExtendedTeamMetrics.empty(), null, null);
    }

    public static Insight build(
            double[][] matrix,
            int pickHome,
            int pickAway,
            double expectedHome,
            double expectedAway,
            boolean marketBased,
            ExtendedTeamMetrics homeExt,
            ExtendedTeamMetrics awayExt,
            String homeCode,
            String awayCode
    ) {
        return build(matrix, pickHome, pickAway, expectedHome, expectedAway, marketBased,
                homeExt, awayExt, homeCode, awayCode, null, null);
    }

    public static Insight build(
            double[][] matrix,
            int pickHome,
            int pickAway,
            double expectedHome,
            double expectedAway,
            boolean marketBased,
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam
    ) {
        ExtendedTeamMetrics homeExt = homeTeam != null ? homeTeam.extendedOrEmpty() : ExtendedTeamMetrics.empty();
        ExtendedTeamMetrics awayExt = awayTeam != null ? awayTeam.extendedOrEmpty() : ExtendedTeamMetrics.empty();
        String homeCode = homeTeam != null ? homeTeam.teamCode() : null;
        String awayCode = awayTeam != null ? awayTeam.teamCode() : null;
        return build(matrix, pickHome, pickAway, expectedHome, expectedAway, marketBased,
                homeExt, awayExt, homeCode, awayCode, homeTeam, awayTeam);
    }

    private static Insight build(
            double[][] matrix,
            int pickHome,
            int pickAway,
            double expectedHome,
            double expectedAway,
            boolean marketBased,
            ExtendedTeamMetrics homeExt,
            ExtendedTeamMetrics awayExt,
            String homeCode,
            String awayCode,
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam
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
                reasons(matrix, pickHome, pickAway, pick, pickEv, mode, expectedHome, expectedAway, marketBased,
                        homeExt != null ? homeExt : ExtendedTeamMetrics.empty(),
                        awayExt != null ? awayExt : ExtendedTeamMetrics.empty(),
                        homeCode, awayCode, homeTeam, awayTeam));
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
            double expectedHome, double expectedAway, boolean marketBased,
            ExtendedTeamMetrics homeExt, ExtendedTeamMetrics awayExt,
            String homeCode, String awayCode,
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam
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
        String marketLine = marketBased
                ? String.format(Locale.US, "Букмекеры: победа хозяев %s, ничья %s, победа гостей %s.",
                pct(home), pct(draw), pct(away))
                : String.format(Locale.US,
                "Коэффициентов пока нет — прогноз только по статистике (хозяева %s, ничья %s, гости %s). Он менее надёжен.",
                pct(home), pct(draw), pct(away));
        String goalsLine = String.format(Locale.US, "Ждём около %.1f : %.1f гола, тотал больше 2.5 — %s.",
                expectedHome, expectedAway, pct(over25));
        String scoreLine = (mode.home() == ph && mode.away() == pa)
                ? String.format(Locale.US,
                "%d:%d — самый вероятный счёт (%s) и он же приносит больше всего очков.",
                ph, pa, pct(pick.exact()))
                : String.format(Locale.US,
                "Самый вероятный счёт %d:%d (%s), но %d:%d выгоднее: в среднем %.2f очка против %.2f — "
                        + "чаще попадает хотя бы в исход или разницу голов.",
                mode.home(), mode.away(), pct(mode.probability()), ph, pa, pickEv, mode.expectedPoints());

        String homeLabel = homeTeam != null ? label(homeCode, homeTeam.teamCode()) : label(homeCode, null);
        String awayLabel = awayTeam != null ? label(awayCode, awayTeam.teamCode()) : label(awayCode, null);
        String homeVenue = venuePart(homeLabel, true, homeTeam);
        String awayVenue = venuePart(awayLabel, false, awayTeam);
        String ssLine = soccerStatsContrast(homeExt, awayExt, homeCode, awayCode);
        boolean pickIsMode = mode.home() == ph && mode.away() == pa;
        String third = pickIsMode ? goalsLine : scoreLine;

        List<String> out = new ArrayList<>(3);
        out.add(marketLine);
        if (homeVenue != null && awayVenue != null) {
            out.add(homeVenue);
            out.add(awayVenue);
        } else if (homeVenue != null || awayVenue != null) {
            out.add(homeVenue != null ? homeVenue : awayVenue);
            out.add(third);
        } else if (ssLine != null) {
            out.add(ssLine);
            out.add(third);
        } else {
            out.add(goalsLine);
            out.add(scoreLine);
        }
        return out;
    }

    static String venueFormReason(
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam,
            String homeCode,
            String awayCode
    ) {
        if (homeTeam == null || awayTeam == null) {
            return null;
        }
        String home = label(homeCode, homeTeam.teamCode());
        String away = label(awayCode, awayTeam.teamCode());
        String homePart = venuePart(home, true, homeTeam);
        String awayPart = venuePart(away, false, awayTeam);
        if (homePart == null || awayPart == null) {
            return null;
        }
        return homePart + " " + awayPart;
    }

    private static String label(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim().toUpperCase(Locale.ROOT);
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String venuePart(String code, boolean homeVenue, TeamStatsSnapshot team) {
        if (code == null || team == null) {
            return null;
        }
        ExtendedTeamMetrics e = team.extendedOrEmpty();
        Double xg = homeVenue ? team.xgHome() : team.xgAway();
        double gf = homeVenue ? team.scoredHome() : team.scoredAway();
        double ga = homeVenue ? team.concededHome() : team.concededAway();
        Double ppg = homeVenue ? e.homePpg() : e.awayPpg();
        boolean hasSample = PoissonScoreModel.hasVenueSample(gf, ga);
        if (xg == null && ppg == null && !hasSample) {
            return null;
        }
        String where = homeVenue ? "дома" : "в гостях";
        StringBuilder sb = new StringBuilder();
        sb.append(code).append(' ').append(where).append(':');
        boolean first = true;
        if (xg != null) {
            sb.append(" xG ").append(fmt(xg));
            first = false;
        }
        if (ppg != null) {
            sb.append(first ? ' ' : ", ").append(fmt(ppg)).append(" очк./матч");
            first = false;
        }
        if (hasSample) {
            sb.append(first ? ' ' : ", ").append(fmt(gf)).append(" гол./матч");
        }
        sb.append('.');
        return sb.toString();
    }

    private static String fmt(double value) {
        if (Math.rint(value) == value && Math.abs(value) < 1_000_000) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    static String soccerStatsContrast(
            ExtendedTeamMetrics homeExt,
            ExtendedTeamMetrics awayExt,
            String homeCode,
            String awayCode
    ) {
        if (homeCode == null || homeCode.isBlank() || awayCode == null || awayCode.isBlank()) {
            return null;
        }
        String home = homeCode.trim().toUpperCase(Locale.ROOT);
        String away = awayCode.trim().toUpperCase(Locale.ROOT);

        Cand best = null;

        Double hOpen = openingPct(homeExt);
        Double aOpen = openingPct(awayExt);
        best = better(best, percentCand(hOpen, aOpen, home, away,
                "чаще открывает счёт в матчах сезона"));

        best = better(best, percentCand(homeExt.ssLeadPct(), awayExt.ssLeadPct(), home, away,
                "дольше ведёт в счёте по сезону"));

        best = better(best, percentCand(homeExt.ssEqualiserScoredPct(), awayExt.ssEqualiserScoredPct(), home, away,
                "чаще сравнивает счёт в матчах сезона"));

        if (best == null || best.score() < 1.0) {
            best = better(best, minuteCand(homeExt.ssAvgFirstGoalMin(), awayExt.ssAvgFirstGoalMin(), home, away));
        }

        return best != null && best.score() >= 1.0 ? best.text() : null;
    }

    private static Double openingPct(ExtendedTeamMetrics ext) {
        if (ext.ssScoredFirstPct() != null) {
            return ext.ssScoredFirstPct();
        }
        return ext.ssOgsPct();
    }

    private static Cand better(Cand cur, Cand next) {
        if (next == null) {
            return cur;
        }
        if (cur == null || next.score() > cur.score()) {
            return next;
        }
        return cur;
    }

    private static Cand percentCand(Double home, Double away, String homeCode, String awayCode, String verb) {
        if (home == null || away == null) {
            return null;
        }
        double gap = Math.abs(home - away);
        double score = gap / 12.0;
        if (score < 1.0) {
            return null;
        }
        boolean homeHigher = home >= away;
        String lead = homeHigher ? homeCode : awayCode;
        double leadV = homeHigher ? home : away;
        double otherV = homeHigher ? away : home;
        String text = String.format(Locale.US,
                "%s %s (%s против %s).",
                lead, verb, pctPoints(leadV), pctPoints(otherV));
        return new Cand(score, text);
    }

    private static Cand minuteCand(Double home, Double away, String homeCode, String awayCode) {
        if (home == null || away == null) {
            return null;
        }
        double gap = Math.abs(home - away);
        double score = gap / 8.0;
        if (score < 1.0) {
            return null;
        }
        boolean homeEarlier = home <= away;
        String lead = homeEarlier ? homeCode : awayCode;
        double leadV = homeEarlier ? home : away;
        double otherV = homeEarlier ? away : home;
        String text = String.format(Locale.US,
                "%s раньше открывает счёт в среднем (%.0f' против %.0f').",
                lead, leadV, otherV);
        return new Cand(score, text);
    }

    private record Cand(double score, String text) {
    }

    private static String pct(double p) {
        return String.format(Locale.US, "%.0f%%", p * 100);
    }

    private static String pctPoints(double points) {
        return String.format(Locale.US, "%.0f%%", points);
    }
}
