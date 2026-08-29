package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

public final class PoissonScoreModel {

    private static final int MAX_GOALS = 5;

    private PoissonScoreModel() {
    }

    public record Result(
            int recommendedHome,
            int recommendedAway,
            double lambdaHome,
            double lambdaAway,
            double scoreProbability,
            List<String> explanationLines,
            String summary
    ) {
    }

    public static Result recommend(
            double lambdaHomeFormula,
            double lambdaAwayFormula,
            Double oddHome,
            Double oddDraw,
            Double oddAway,
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsLeagueSnapshot league
    ) {
        String homeCode = home.teamCode();
        String awayCode = away.teamCode();
        FootyStatsExtendedMetrics homeExt = home.extendedOrEmpty();
        FootyStatsExtendedMetrics awayExt = away.extendedOrEmpty();

        double blendedHome = buildLambda(
                lambdaHomeFormula,
                home.xgOverall(),
                away.xgaOverall(),
                home.scoredHome(),
                homeExt.seasonScoredHome(),
                homeExt.formAvgGoalsHome(),
                homeExt.homePpg(),
                homeExt.xPtsDelta(),
                homeExt.homeAdvantage(),
                true
        );
        double blendedAway = buildLambda(
                lambdaAwayFormula,
                away.xgOverall(),
                home.xgaOverall(),
                away.scoredAway(),
                awayExt.seasonScoredAway(),
                awayExt.formAvgGoalsAway(),
                awayExt.awayPpg(),
                awayExt.xPtsDelta(),
                null,
                false
        );

        double[][] matrix = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
            double pHome = poisson(homeGoals, blendedHome);
            for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
                double joint = pHome * poisson(awayGoals, blendedAway);
                joint *= scoreWeight(homeGoals, awayGoals, homeExt, awayExt);
                matrix[homeGoals][awayGoals] = joint;
                total += joint;
            }
        }

        int bestHome = 0;
        int bestAway = 0;
        double bestProb = -1;
        if (total > 0) {
            for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
                for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
                    double normalized = matrix[homeGoals][awayGoals] / total;
                    matrix[homeGoals][awayGoals] = normalized;
                    if (normalized > bestProb) {
                        bestProb = normalized;
                        bestHome = homeGoals;
                        bestAway = awayGoals;
                    }
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.US,
                "Модель: λ(%s) = %.2f, λ(%s) = %.2f",
                homeCode,
                blendedHome,
                awayCode,
                blendedAway
        ));
        lines.add(String.format(
                Locale.US,
                "Формула хозяев: (%.2f заб × %.2f проп. гостей) / %.2f ср. лиги дома = %.2f",
                home.scoredHome(),
                away.concededAway(),
                league.avgHomeScored(),
                lambdaHomeFormula
        ));
        lines.add(String.format(
                Locale.US,
                "Формула гостей: (%.2f заб × %.2f проп. хозяев) / %.2f ср. лиги в гостях = %.2f",
                away.scoredAway(),
                home.concededHome(),
                league.avgAwayScored(),
                lambdaAwayFormula
        ));
        appendIfPresent(lines, homeCode, awayCode, home.xgOverall(), away.xgOverall(), home.xgaOverall(), away.xgaOverall());
        appendFormLines(lines, homeExt, awayExt);
        appendSeasonLines(lines, homeExt, awayExt, homeCode, awayCode);
        appendXptsLines(lines, homeExt, awayExt, homeCode, awayCode);
        if (oddHome != null && oddDraw != null && oddAway != null) {
            lines.add(String.format(
                    Locale.US,
                    "Коэффициенты букмекеров: 1=%.2f, X=%.2f, 2=%.2f",
                    oddHome,
                    oddDraw,
                    oddAway
            ));
        }
        lines.add(String.format(
                Locale.US,
                "Наиболее вероятный счёт (Пуассон + коррекции BTTS/CS/ничьих/тотала): %d:%d (%.1f%%)",
                bestHome,
                bestAway,
                bestProb * 100
        ));
        lines.add("Источник: FootyStats (form, xG, xPts, BTTS, CS, draws, over/under, home-away).");

        return new Result(
                bestHome,
                bestAway,
                blendedHome,
                blendedAway,
                bestProb,
                lines,
                bestHome + ":" + bestAway
        );
    }

    private static double buildLambda(
            double formulaLambda,
            Double teamXg,
            Double opponentXga,
            double formScored,
            Double seasonScored,
            Double formAvgGoals,
            Double ppg,
            Double xPtsDelta,
            Double homeAdvantage,
            boolean isHome
    ) {
        double lambda = formulaLambda * 0.40;
        if (teamXg != null && teamXg > 0) {
            lambda += teamXg * 0.22;
        }
        if (opponentXga != null && opponentXga > 0) {
            lambda += opponentXga * 0.13;
        }
        lambda += formScored * 0.12;
        if (seasonScored != null && seasonScored > 0) {
            lambda += seasonScored * 0.08;
        }
        if (formAvgGoals != null && formAvgGoals > 0) {
            lambda += formAvgGoals * 0.05;
        }
        if (ppg != null && ppg > 0) {
            lambda *= 0.92 + Math.min(ppg, 3.0) * 0.04;
        }
        if (xPtsDelta != null && xPtsDelta < 0) {
            lambda *= 1.04;
        } else if (xPtsDelta != null && xPtsDelta > 3) {
            lambda *= 0.97;
        }
        if (isHome && homeAdvantage != null) {
            lambda *= 1.0 + Math.max(-0.15, Math.min(0.25, (homeAdvantage - 8.0) * 0.02));
        }
        return clampLambda(lambda);
    }

    private static double scoreWeight(
            int homeGoals,
            int awayGoals,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away
    ) {
        double weight = 1.0;
        int total = homeGoals + awayGoals;

        if (homeGoals == 0) {
            weight *= 0.75 + pct(home.formCsHome(), home.seasonCsHome()) * 0.5;
            weight *= 0.80 + pct(away.ftsAway()) * 0.45;
        }
        if (awayGoals == 0) {
            weight *= 0.75 + pct(away.formCsAway(), away.seasonCsAway()) * 0.5;
            weight *= 0.80 + pct(home.ftsHome()) * 0.45;
        }

        double btts = avg(
                home.formBttsHome(),
                home.seasonBttsHome(),
                away.formBttsAway(),
                away.seasonBttsAway()
        );
        if (homeGoals > 0 && awayGoals > 0) {
            weight *= 0.65 + btts * 0.7;
        } else if (homeGoals == 0 || awayGoals == 0) {
            weight *= 1.35 - btts * 0.55;
        }

        if (homeGoals == awayGoals && homeGoals > 0) {
            double draw = avg(home.drawPctHome(), away.drawPctAway(), home.drawPctOverall());
            weight *= 0.75 + draw * 0.65;
        }

        double over25 = avg(home.over25Home(), away.over25Away(), home.over25Overall());
        double under25 = avg(home.under25Home(), away.under25Away(), home.under25Overall());
        if (total >= 3) {
            weight *= 0.70 + over25 * 0.65;
        }
        if (total <= 2) {
            double underSignal = under25 > 0 ? under25 : (100 - over25);
            weight *= 0.70 + underSignal * 0.65;
        }

        double htBias = avg(home.htPpg(), away.htPpg());
        if (htBias > 0 && total <= 1) {
            weight *= 1.05;
        }
        if (avg(home.secondHalfPpg(), away.secondHalfPpg()) > htBias && total >= 3) {
            weight *= 1.04;
        }

        return Math.max(0.05, weight);
    }

    private static void appendIfPresent(
            List<String> lines,
            String homeCode,
            String awayCode,
            Double homeXg,
            Double awayXg,
            Double homeXga,
            Double awayXga
    ) {
        if (homeXg != null && awayXg != null) {
            lines.add(String.format(
                    Locale.US,
                    "xG / xGA: %s %.2f / %.2f, %s %.2f / %.2f",
                    homeCode,
                    homeXg,
                    nz(homeXga),
                    awayCode,
                    awayXg,
                    nz(awayXga)
            ));
        }
    }

    private static void appendFormLines(List<String> lines, FootyStatsExtendedMetrics home, FootyStatsExtendedMetrics away) {
        if (home.formBttsHome() != null || away.formBttsAway() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Форма (6 игр): BTTS дома %.0f%%, в гостях %.0f%%; CS дома %.0f%%, в гостях %.0f%%",
                    nz(home.formBttsHome()),
                    nz(away.formBttsAway()),
                    nz(home.formCsHome()),
                    nz(away.formCsAway())
            ));
        }
    }

    private static void appendSeasonLines(
            List<String> lines,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            String homeCode,
            String awayCode
    ) {
        if (home.seasonBttsHome() != null || away.over25Away() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Сезон: Over 2.5 %s %.0f%% / %s %.0f%%; ничьи %.0f%% / %.0f%%",
                    homeCode,
                    nz(home.over25Home()),
                    awayCode,
                    nz(away.over25Away()),
                    nz(home.drawPctHome()),
                    nz(away.drawPctAway())
            ));
        }
    }

    private static void appendXptsLines(
            List<String> lines,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            String homeCode,
            String awayCode
    ) {
        if (home.xPts() != null || away.xPts() != null) {
            lines.add(String.format(
                    Locale.US,
                    "xPts: %s %.1f (факт %.0f, Δ %+.1f); %s %.1f (факт %.0f, Δ %+.1f)",
                    homeCode,
                    nz(home.xPts()),
                    nz(home.actualPts()),
                    nz(home.xPtsDelta()),
                    awayCode,
                    nz(away.xPts()),
                    nz(away.actualPts()),
                    nz(away.xPtsDelta())
            ));
        }
        if (home.homeAdvantage() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Home Advantage %s: %.1f; PPG дома %.2f / в гостях %.2f",
                    homeCode,
                    home.homeAdvantage(),
                    nz(home.homePpg()),
                    nz(away.awayPpg())
            ));
        }
    }

    private static double pct(Double... values) {
        return avg(values) / 100.0;
    }

    private static double avg(Double... values) {
        double sum = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 50.0 : sum / count;
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    private static double clampLambda(double lambda) {
        if (Double.isNaN(lambda) || Double.isInfinite(lambda) || lambda < 0) {
            return 0.1;
        }
        return Math.min(lambda, 4.5);
    }

    private static double poisson(int goals, double lambda) {
        if (lambda <= 0) {
            return goals == 0 ? 1.0 : 0.0;
        }
        return Math.exp(-lambda) * Math.pow(lambda, goals) / factorial(goals);
    }

    private static double factorial(int n) {
        double result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}
