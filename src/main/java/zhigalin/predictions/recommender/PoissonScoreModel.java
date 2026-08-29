package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

/**
 * Score recommender based on attack/defense strengths (with early-season shrinkage),
 * xG/xGA, and bookmaker 1X2 probabilities.
 *
 * <p>Independent Poisson mode is {@code floor(λ)} — if λ stays below 1.0 (common when
 * home/away form splits are still zero after 1 match), every tip collapses to 0:0.
 * This model therefore shrinks thin samples toward league averages and blends in a
 * market-implied goal prior so λ lands in a realistic PL range (~1.0–2.0).
 */
public final class PoissonScoreModel {

    private static final int MAX_GOALS = 5;
    /** Pseudo-matches for Bayesian shrinkage toward league average. */
    private static final double SHRINK_K = 4.0;
    private static final double DEFAULT_HOME_AVG = 1.48;
    private static final double DEFAULT_AWAY_AVG = 1.25;

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

        double leagueHome = positiveOr(league.avgHomeScored(), DEFAULT_HOME_AVG);
        double leagueAway = positiveOr(league.avgAwayScored(), DEFAULT_AWAY_AVG);

        double homeAttack = attackStrength(
                home.scoredHome(), home.scoredOverall(),
                homeExt.seasonScoredHome(), homeExt.seasonScoredPerMatch(),
                leagueHome
        );
        double awayDefense = defenseStrength(
                away.concededAway(), away.concededOverall(),
                awayExt.seasonConcededAway(), awayExt.seasonConcededPerMatch(),
                leagueHome
        );
        double awayAttack = attackStrength(
                away.scoredAway(), away.scoredOverall(),
                awayExt.seasonScoredAway(), awayExt.seasonScoredPerMatch(),
                leagueAway
        );
        double homeDefense = defenseStrength(
                home.concededHome(), home.concededOverall(),
                homeExt.seasonConcededHome(), homeExt.seasonConcededPerMatch(),
                leagueAway
        );

        double formulaHome = leagueHome * homeAttack * awayDefense;
        double formulaAway = leagueAway * awayAttack * homeDefense;

        Double homeXgPerMatch = perMatchRate(home.xgOverall(), homeExt);
        Double awayXgPerMatch = perMatchRate(away.xgOverall(), awayExt);
        Double homeXgaPerMatch = perMatchRate(home.xgaOverall(), homeExt);
        Double awayXgaPerMatch = perMatchRate(away.xgaOverall(), awayExt);

        double xgHome = blendPair(homeXgPerMatch, awayXgaPerMatch, leagueHome);
        double xgAway = blendPair(awayXgPerMatch, homeXgaPerMatch, leagueAway);

        MarketLambdas marketLambdas = marketLambdas(market, leagueHome, leagueAway);

        double thinSample = thinSampleFactor(home, away);
        double statsWeight = 0.55 - 0.25 * thinSample;
        double xgWeight = 0.25;
        double marketWeight = 0.20 + 0.25 * thinSample;
        double weightSum = statsWeight + xgWeight + marketWeight;

        double blendedHome = (
                formulaHome * statsWeight
                + xgHome * xgWeight
                + marketLambdas.home() * marketWeight
        ) / weightSum;
        double blendedAway = (
                formulaAway * statsWeight
                + xgAway * xgWeight
                + marketLambdas.away() * marketWeight
        ) / weightSum;

        blendedHome = applyHomeAdvantage(blendedHome, homeExt.homeAdvantage());
        blendedHome = applyXptsNudge(blendedHome, homeExt.xPtsDelta());
        blendedAway = applyXptsNudge(blendedAway, awayExt.xPtsDelta());

        blendedHome = clampLambda(blendedHome);
        blendedAway = clampLambda(blendedAway);

        double[][] matrix = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
            double pHome = poisson(homeGoals, blendedHome);
            for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
                double joint = pHome * poisson(awayGoals, blendedAway);
                joint *= scoreWeight(homeGoals, awayGoals, homeExt, awayExt, thinSample);
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
                "Силы: атака дома %.2f × оборона гостей %.2f → %.2f; атака гостей %.2f × оборона хозяев %.2f → %.2f",
                homeAttack,
                awayDefense,
                formulaHome,
                awayAttack,
                homeDefense,
                formulaAway
        ));
        lines.add(String.format(
                Locale.US,
                "Смешивание: статистика %.0f%%, xG %.0f%%, рынок %.0f%%%s",
                statsWeight / weightSum * 100,
                xgWeight / weightSum * 100,
                marketWeight / weightSum * 100,
                thinSample > 0.4 ? " (мало матчей — усилен рынок/лига)" : ""
        ));
        if (homeXgPerMatch != null || awayXgPerMatch != null) {
            lines.add(String.format(
                    Locale.US,
                    "xG/xGA за матч: %s %s / %s, %s %s / %s",
                    homeCode,
                    fmt(homeXgPerMatch),
                    fmt(homeXgaPerMatch),
                    awayCode,
                    fmt(awayXgPerMatch),
                    fmt(awayXgaPerMatch)
            ));
        }
        appendFormLines(lines, homeExt, awayExt);
        appendSeasonLines(lines, homeExt, awayExt, homeCode, awayCode);
        if (market != null) {
            lines.add(String.format(
                    Locale.US,
                    "Букмекеры: 1=%.0f%% X=%.0f%% 2=%.0f%% → λ рынка %.2f : %.2f (коэф. %.2f / %.2f / %.2f)",
                    market.homeWin() * 100,
                    market.draw() * 100,
                    market.awayWin() * 100,
                    marketLambdas.home(),
                    marketLambdas.away(),
                    oddHome,
                    oddDraw,
                    oddAway
            ));
        }
        lines.add(String.format(
                Locale.US,
                "Рекомендуемый счёт: %d:%d (%.1f%%)",
                bestHome,
                bestAway,
                bestProb * 100
        ));

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

    private record MarketLambdas(double home, double away) {
    }

    private static MarketLambdas marketLambdas(MarketOutcome market, double leagueHome, double leagueAway) {
        if (market == null) {
            return new MarketLambdas(leagueHome, leagueAway);
        }
        double totalBase = leagueHome + leagueAway;
        // Favourites score more; draws keep total near league average.
        double total = totalBase * (0.92 + market.homeWin() * 0.08 + market.awayWin() * 0.05);
        if (market.draw() > 0.30) {
            total *= 0.94;
        }
        double homeShare = 0.42 + market.homeWin() * 0.28 - market.awayWin() * 0.12;
        homeShare = Math.max(0.35, Math.min(0.70, homeShare));
        return new MarketLambdas(total * homeShare, total * (1.0 - homeShare));
    }

    private static double attackStrength(
            double venueForm,
            double overallForm,
            Double seasonVenue,
            Double seasonOverall,
            double leagueAvg
    ) {
        double observed = firstPositive(venueForm, overallForm, nz(seasonVenue), nz(seasonOverall), leagueAvg);
        return shrink(observed, leagueAvg);
    }

    private static double defenseStrength(
            double venueForm,
            double overallForm,
            Double seasonVenue,
            Double seasonOverall,
            double leagueAvg
    ) {
        double observed = firstPositive(venueForm, overallForm, nz(seasonVenue), nz(seasonOverall), leagueAvg);
        return shrink(observed, leagueAvg);
    }

    /** Strength relative to league; shrink toward 1.0 so one match cannot zero the rate. */
    private static double shrink(double observedPerMatch, double leagueAvg) {
        if (leagueAvg <= 0) {
            return 1.0;
        }
        double sampleN = observedPerMatch <= 0 ? 0.5 : 1.5;
        double bayes = (observedPerMatch * sampleN + leagueAvg * SHRINK_K) / (sampleN + SHRINK_K);
        return bayes / leagueAvg;
    }

    private static double thinSampleFactor(FootyStatsTeamSnapshot home, FootyStatsTeamSnapshot away) {
        double homeVenue = home.scoredHome() + home.concededHome();
        double awayVenue = away.scoredAway() + away.concededAway();
        // 0 = enough venue signal, 1 = almost no venue goals yet
        double homeThin = homeVenue <= 0.01 ? 1.0 : homeVenue < 1.5 ? 0.6 : 0.15;
        double awayThin = awayVenue <= 0.01 ? 1.0 : awayVenue < 1.5 ? 0.6 : 0.15;
        return (homeThin + awayThin) / 2.0;
    }

    private static Double perMatchRate(Double seasonOrMatchValue, FootyStatsExtendedMetrics ext) {
        if (seasonOrMatchValue == null || seasonOrMatchValue <= 0) {
            return null;
        }
        // FootyStats xG table is usually season totals; early season totals look like per-match.
        // If value is clearly a season sum, divide by estimated matches from xPts/actual pts.
        if (seasonOrMatchValue <= 3.5) {
            return seasonOrMatchValue;
        }
        double matches = estimateMatchesPlayed(ext);
        if (matches >= 2) {
            return seasonOrMatchValue / matches;
        }
        return seasonOrMatchValue / Math.max(2.0, seasonOrMatchValue / 2.0);
    }

    private static double estimateMatchesPlayed(FootyStatsExtendedMetrics ext) {
        if (ext.actualPts() != null && ext.homePpg() != null && ext.homePpg() > 0) {
            return Math.max(1.0, ext.actualPts() / Math.max(0.5, (nz(ext.homePpg()) + nz(ext.awayPpg())) / 2.0));
        }
        if (ext.xPts() != null && ext.xPts() > 0) {
            return Math.max(1.0, ext.xPts() / 1.4);
        }
        return 0;
    }

    private static double blendPair(Double teamFor, Double opponentAgainst, double leagueAvg) {
        if (teamFor != null && opponentAgainst != null) {
            return (teamFor + opponentAgainst) / 2.0;
        }
        if (teamFor != null) {
            return teamFor;
        }
        if (opponentAgainst != null) {
            return opponentAgainst;
        }
        return leagueAvg;
    }

    private static double applyHomeAdvantage(double lambda, Double homeAdvantage) {
        if (homeAdvantage == null) {
            return lambda * 1.05;
        }
        return lambda * (1.0 + Math.max(-0.12, Math.min(0.18, (homeAdvantage - 8.0) * 0.015)));
    }

    private static double applyXptsNudge(double lambda, Double xPtsDelta) {
        if (xPtsDelta == null) {
            return lambda;
        }
        // Negative delta = underperforming xPts → slight uplift expectation of regression
        if (xPtsDelta < -1.5) {
            return lambda * 1.04;
        }
        if (xPtsDelta > 3) {
            return lambda * 0.97;
        }
        return lambda;
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
        return 0.75 + outcomeProb * 0.55;
    }

    private static double scoreWeight(
            int homeGoals,
            int awayGoals,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            double thinSample
    ) {
        double weight = 1.0;
        int total = homeGoals + awayGoals;
        // Early season CS/FTS from 1 match are noisy — dampen
        double sampleScale = 1.0 - 0.65 * thinSample;

        if (homeGoals == 0) {
            weight *= 1.0 + (cleanSheetBoost(home.formCsHome(), home.seasonCsHome()) - 1.0) * sampleScale;
            weight *= 1.0 + (failedToScoreBoost(away.ftsAway()) - 1.0) * sampleScale;
        }
        if (awayGoals == 0) {
            weight *= 1.0 + (cleanSheetBoost(away.formCsAway(), away.seasonCsAway()) - 1.0) * sampleScale;
            weight *= 1.0 + (failedToScoreBoost(home.ftsHome()) - 1.0) * sampleScale;
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
                weight *= 0.90 + ratio * 0.20;
            } else if (homeGoals == 0 || awayGoals == 0) {
                weight *= 1.10 - ratio * 0.18;
            }
        }

        if (homeGoals == awayGoals && homeGoals > 0) {
            Double draw = avgNullable(home.drawPctHome(), away.drawPctAway(), home.drawPctOverall());
            if (draw != null) {
                weight *= 0.92 + (draw / 100.0) * 0.16;
            }
        }

        Double over25 = avgNullable(home.over25Home(), away.over25Away(), home.over25Overall());
        Double under25 = avgNullable(home.under25Home(), away.under25Away(), home.under25Overall());
        if (total >= 3 && over25 != null) {
            weight *= 0.90 + (over25 / 100.0) * 0.20;
        }
        if (total <= 1 && under25 != null) {
            weight *= 0.90 + (under25 / 100.0) * 0.18;
        }

        return Math.max(0.15, weight);
    }

    private static double cleanSheetBoost(Double... values) {
        Double pct = avgNullable(values);
        if (pct == null) {
            return 1.0;
        }
        return 0.96 + (pct / 100.0) * 0.08;
    }

    private static double failedToScoreBoost(Double ftsPct) {
        if (ftsPct == null) {
            return 1.0;
        }
        return 0.96 + (ftsPct / 100.0) * 0.08;
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

    private static void appendFormLines(List<String> lines, FootyStatsExtendedMetrics home, FootyStatsExtendedMetrics away) {
        if (home.formBttsHome() != null || away.formBttsAway() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Форма: BTTS дома %.0f%% / в гостях %.0f%%; CS %.0f%% / %.0f%%",
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
        if (home.seasonScoredHome() != null || away.seasonScoredAway() != null || home.over25Home() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Сезон голы: %s дом %.2f, %s выезд %.2f; Over2.5 %.0f%% / %.0f%%",
                    homeCode,
                    nz(home.seasonScoredHome(), home.seasonScoredPerMatch()),
                    awayCode,
                    nz(away.seasonScoredAway(), away.seasonScoredPerMatch()),
                    nz(home.over25Home()),
                    nz(away.over25Away())
            ));
        }
    }

    private static double firstPositive(double... values) {
        for (double value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private static double positiveOr(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    private static double nz(Double primary, Double fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? 0 : fallback;
    }

    private static String fmt(Double value) {
        return value == null ? "—" : String.format(Locale.US, "%.2f", value);
    }

    private static double clampLambda(double lambda) {
        if (Double.isNaN(lambda) || Double.isInfinite(lambda) || lambda < 0) {
            return DEFAULT_AWAY_AVG;
        }
        return Math.max(0.7, Math.min(lambda, 4.0));
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
