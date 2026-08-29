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

    public record MarketOutcome(double homeWin, double draw, double awayWin) {
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
        MarketOutcome market = marketOutcome(oddHome, oddDraw, oddAway);

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
                true,
                market
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
                false,
                market
        );

        double[][] matrix = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
            double pHome = poisson(homeGoals, blendedHome);
            for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
                double joint = pHome * poisson(awayGoals, blendedAway);
                joint *= scoreWeight(homeGoals, awayGoals, homeExt, awayExt);
                if (market != null) {
                    joint *= marketScoreWeight(homeGoals, awayGoals, market);
                }
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
        if (market != null) {
            lines.add(String.format(
                    Locale.US,
                    "Букмекеры (норм.): 1=%.0f%%, X=%.0f%%, 2=%.0f%% (коэф. %.2f / %.2f / %.2f)",
                    market.homeWin() * 100,
                    market.draw() * 100,
                    market.awayWin() * 100,
                    oddHome,
                    oddDraw,
                    oddAway
            ));
        }
        lines.add(String.format(
                Locale.US,
                "Наиболее вероятный счёт (Пуассон + FootyStats + рынок): %d:%d (%.1f%%)",
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

    static MarketOutcome marketOutcome(Double oddHome, Double oddDraw, Double oddAway) {
        if (oddHome == null || oddDraw == null || oddAway == null
            || oddHome <= 1.0 || oddDraw <= 1.0 || oddAway <= 1.0) {
            return null;
        }
        double invHome = 1.0 / oddHome;
        double invDraw = 1.0 / oddDraw;
        double invAway = 1.0 / oddAway;
        double sum = invHome + invDraw + invAway;
        if (sum <= 0) {
            return null;
        }
        return new MarketOutcome(invHome / sum, invDraw / sum, invAway / sum);
    }

    private static double marketScoreWeight(int homeGoals, int awayGoals, MarketOutcome market) {
        double outcomeProb;
        if (homeGoals > awayGoals) {
            outcomeProb = market.homeWin();
        } else if (homeGoals < awayGoals) {
            outcomeProb = market.awayWin();
        } else {
            outcomeProb = market.draw();
        }
        return 0.82 + outcomeProb * 0.36;
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
            boolean isHome,
            MarketOutcome market
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
        if (market != null) {
            double marketBoost = isHome ? market.homeWin() : market.awayWin();
            lambda *= 0.88 + marketBoost * 0.24;
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
            weight *= cleanSheetBoost(home.formCsHome(), home.seasonCsHome());
            weight *= failedToScoreBoost(away.ftsAway());
        }
        if (awayGoals == 0) {
            weight *= cleanSheetBoost(away.formCsAway(), away.seasonCsAway());
            weight *= failedToScoreBoost(home.ftsHome());
        }

        Double btts = avgNullable(
                home.formBttsHome(),
                home.seasonBttsHome(),
                away.formBttsAway(),
                away.seasonBttsAway()
        );
        if (btts != null) {
            double ratio = btts / 100.0;
            if (homeGoals > 0 && awayGoals > 0) {
                weight *= 0.88 + ratio * 0.24;
            } else if (homeGoals == 0 || awayGoals == 0) {
                weight *= 1.12 - ratio * 0.22;
            }
        }

        if (homeGoals == awayGoals && homeGoals > 0) {
            Double draw = avgNullable(home.drawPctHome(), away.drawPctAway(), home.drawPctOverall());
            if (draw != null) {
                weight *= 0.90 + (draw / 100.0) * 0.20;
            }
        }

        Double over25 = avgNullable(home.over25Home(), away.over25Away(), home.over25Overall());
        Double under25 = avgNullable(home.under25Home(), away.under25Away(), home.under25Overall());
        if (total >= 3 && over25 != null) {
            weight *= 0.88 + (over25 / 100.0) * 0.24;
        }
        if (total <= 2) {
            double underSignal = under25 != null ? under25 : over25 != null ? (100.0 - over25) : Double.NaN;
            if (!Double.isNaN(underSignal)) {
                weight *= 0.88 + (underSignal / 100.0) * 0.24;
            }
        }

        return Math.max(0.05, weight);
    }

    private static double cleanSheetBoost(Double... values) {
        Double pct = avgNullable(values);
        if (pct == null) {
            return 1.0;
        }
        return 0.94 + (pct / 100.0) * 0.12;
    }

    private static double failedToScoreBoost(Double ftsPct) {
        if (ftsPct == null) {
            return 1.0;
        }
        return 0.94 + (ftsPct / 100.0) * 0.12;
    }

    private static Double avgNullable(Double... values) {
        double sum = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
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
