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
        return recommend(oddHome, oddDraw, oddAway, home, away, league, null);
    }

    public static Result recommend(
            Double oddHome,
            Double oddDraw,
            Double oddAway,
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsLeagueSnapshot league,
            H2hStats h2h
    ) {
        String homeCode = home.teamCode();
        String awayCode = away.teamCode();
        FootyStatsExtendedMetrics homeExt = home.extendedOrEmpty();
        FootyStatsExtendedMetrics awayExt = away.extendedOrEmpty();
        MarketOutcome market = marketOutcome(oddHome, oddDraw, oddAway);

        double leagueHome = positiveOr(league.avgHomeScored(), DEFAULT_HOME_AVG);
        double leagueAway = positiveOr(league.avgAwayScored(), DEFAULT_AWAY_AVG);

        double homeAttack = attackStrength(
                home.scoredHome(), home.concededHome(),
                home.scoredOverall(),
                homeExt.seasonScoredHome(), homeExt.seasonScoredPerMatch(),
                leagueHome
        );
        double awayDefense = defenseStrength(
                away.concededAway(), away.scoredAway(),
                away.concededOverall(),
                awayExt.seasonConcededAway(), awayExt.seasonConcededPerMatch(),
                leagueHome
        );
        double awayAttack = attackStrength(
                away.scoredAway(), away.concededAway(),
                away.scoredOverall(),
                awayExt.seasonScoredAway(), awayExt.seasonScoredPerMatch(),
                leagueAway
        );
        double homeDefense = defenseStrength(
                home.concededHome(), home.scoredHome(),
                home.concededOverall(),
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
        blendedHome = applySoccerStatsLambdaNudge(blendedHome, homeExt, true, thinSample);
        blendedAway = applySoccerStatsLambdaNudge(blendedAway, awayExt, false, thinSample);
        blendedHome = applyH2hLambda(blendedHome, h2h, true);
        blendedAway = applyH2hLambda(blendedAway, h2h, false);

        blendedHome = clampLambda(blendedHome);
        blendedAway = clampLambda(blendedAway);

        double[][] matrix = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
            double pHome = poisson(homeGoals, blendedHome);
            for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
                double joint = pHome * poisson(awayGoals, blendedAway);
                joint *= scoreWeight(homeGoals, awayGoals, home, away, homeExt, awayExt, thinSample);
                joint *= soccerStatsScoreWeight(homeGoals, awayGoals, homeExt, awayExt, thinSample);
                if (market != null) {
                    joint *= marketScoreWeight(homeGoals, awayGoals, market);
                }
                joint *= h2hScoreWeight(homeGoals, awayGoals, h2h);
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

        List<String> lines = buildHumanExplanation(
                homeCode,
                awayCode,
                blendedHome,
                blendedAway,
                homeAttack,
                awayDefense,
                awayAttack,
                homeDefense,
                thinSample,
                homeXgPerMatch,
                homeXgaPerMatch,
                awayXgPerMatch,
                awayXgaPerMatch,
                home,
                away,
                homeExt,
                awayExt,
                h2h,
                market,
                oddHome,
                oddDraw,
                oddAway,
                bestHome,
                bestAway,
                bestProb
        );

        String summary = String.format(
                Locale.US,
                "Ожидаемые голы %.1f : %.1f · шанс счёта %.0f%%",
                blendedHome,
                blendedAway,
                bestProb * 100
        );

        return new Result(
                bestHome,
                bestAway,
                blendedHome,
                blendedAway,
                bestProb,
                lines,
                summary
        );
    }

    static List<String> buildHumanExplanation(
            String homeCode,
            String awayCode,
            double blendedHome,
            double blendedAway,
            double homeAttack,
            double awayDefense,
            double awayAttack,
            double homeDefense,
            double thinSample,
            Double homeXgPerMatch,
            Double homeXgaPerMatch,
            Double awayXgPerMatch,
            Double awayXgaPerMatch,
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsExtendedMetrics homeExt,
            FootyStatsExtendedMetrics awayExt,
            H2hStats h2h,
            MarketOutcome market,
            Double oddHome,
            Double oddDraw,
            Double oddAway,
            int bestHome,
            int bestAway,
            double bestProb
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.US,
                "Ожидаемые голы: %s примерно %.1f, %s примерно %.1f",
                homeCode,
                blendedHome,
                awayCode,
                blendedAway
        ));
        lines.add(String.format(
                Locale.ROOT,
                "%s дома: атака %s, оборона %s. %s в гостях: атака %s, оборона %s",
                homeCode,
                attackLabel(homeAttack),
                defenseLabel(homeDefense),
                awayCode,
                attackLabel(awayAttack),
                defenseLabel(awayDefense)
        ));
        if (thinSample > 0.4) {
            lines.add("Мало сыгранных матчей — сильнее учтены коэффициенты букмекеров и средние по лиге");
        }
        if (homeXgPerMatch != null || awayXgPerMatch != null) {
            lines.add(String.format(
                    Locale.US,
                    "По xG: %s создают %s и пропускают %s за матч; %s — %s / %s",
                    homeCode,
                    fmt(homeXgPerMatch),
                    fmt(homeXgaPerMatch),
                    awayCode,
                    fmt(awayXgPerMatch),
                    fmt(awayXgaPerMatch)
            ));
        }
        appendFormLines(lines, home, away, homeExt, awayExt);
        appendSeasonLines(lines, home, away, homeExt, awayExt, homeCode, awayCode);
        appendSoccerStatsLines(lines, homeExt, awayExt, homeCode, awayCode);
        appendH2hLines(lines, h2h, homeCode);
        if (market != null) {
            lines.add(String.format(
                    Locale.US,
                    "Букмекеры: победа хозяев %.0f%%, ничья %.0f%%, гости %.0f%% (коэф. %.2f / %.2f / %.2f)",
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
                "Самый вероятный счёт — %d:%d (около %.0f%%)",
                bestHome,
                bestAway,
                bestProb * 100
        ));
        return lines;
    }

    /** Relative attack vs league average (~1.0). */
    static String attackLabel(double strength) {
        if (strength >= 1.25) {
            return "сильная";
        }
        if (strength >= 1.08) {
            return "выше средней";
        }
        if (strength <= 0.75) {
            return "слабая";
        }
        if (strength <= 0.92) {
            return "ниже средней";
        }
        return "средняя";
    }

    /** Higher value = more goals conceded relative to league. */
    static String defenseLabel(double strength) {
        if (strength >= 1.25) {
            return "дырявая";
        }
        if (strength >= 1.08) {
            return "мягкая";
        }
        if (strength <= 0.75) {
            return "жёсткая";
        }
        if (strength <= 0.92) {
            return "плотная";
        }
        return "средняя";
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
            double venueScored,
            double venueConceded,
            double overallForm,
            Double seasonVenue,
            Double seasonOverall,
            double leagueAvg
    ) {
        if (!hasVenueSample(venueScored, venueConceded)) {
            // No home/away matches yet — do not borrow goals from the other venue.
            return 1.0;
        }
        double observed = firstPositive(venueScored, nz(seasonVenue), overallForm, nz(seasonOverall), leagueAvg);
        return shrink(observed, leagueAvg);
    }

    private static double defenseStrength(
            double venueConceded,
            double venueScored,
            double overallForm,
            Double seasonVenue,
            Double seasonOverall,
            double leagueAvg
    ) {
        if (!hasVenueSample(venueScored, venueConceded)) {
            return 1.0;
        }
        double observed = firstPositive(venueConceded, nz(seasonVenue), overallForm, nz(seasonOverall), leagueAvg);
        return shrink(observed, leagueAvg);
    }

    /** True when the team has at least one goal event in that venue (played there). */
    static boolean hasVenueSample(double venueScored, double venueConceded) {
        return venueScored + venueConceded > 0.01;
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

    private static double applySoccerStatsLambdaNudge(
            double lambda,
            FootyStatsExtendedMetrics ext,
            boolean homeSide,
            double thinSample
    ) {
        double scale = 1.0 - 0.55 * thinSample;
        double factor = 1.0;

        Double scoredFirst = ext.ssScoredFirstPct() != null ? ext.ssScoredFirstPct() : ext.ssOgsPct();
        if (scoredFirst != null) {
            // Teams that open scoring more often tend to finish with a slightly higher λ.
            factor *= 1.0 + ((scoredFirst / 100.0) - 0.45) * 0.06 * scale;
        }
        if (ext.ssLeadPct() != null) {
            factor *= 1.0 + ((ext.ssLeadPct() / 100.0) - 0.35) * 0.05 * scale;
        }
        if (ext.ssFavouritePpg() != null && homeSide) {
            // Strong favourites at home: small uplift.
            factor *= 1.0 + ((ext.ssFavouritePpg() - 1.5) / 3.0) * 0.04 * scale;
        }
        if (ext.ssAvgFirstGoalMin() != null && ext.ssAvgFirstGoalMin() > 0 && ext.ssAvgFirstGoalMin() < 25) {
            factor *= 1.0 + 0.02 * scale;
        }
        return lambda * Math.max(0.92, Math.min(1.08, factor));
    }

    static double applyH2hLambda(double lambda, H2hStats h2h, boolean homeSide) {
        if (h2h == null || h2h.overallGames() < 2) {
            return lambda;
        }
        double overallAvg = homeSide ? h2h.avgGoalsCurrentHomeOverall() : h2h.avgGoalsCurrentAwayOverall();
        double venueAvg = homeSide ? h2h.avgGoalsCurrentHomeAtVenue() : h2h.avgGoalsCurrentAwayAtVenue();
        double target;
        if (h2h.venueGames() >= 2) {
            target = 0.55 * venueAvg + 0.45 * overallAvg;
        } else if (h2h.venueGames() == 1) {
            target = 0.30 * venueAvg + 0.70 * overallAvg;
        } else {
            target = overallAvg;
        }
        double weight = 0.10 * Math.min(1.0, h2h.overallGames() / 6.0)
                + 0.10 * Math.min(1.0, h2h.venueGames() / 4.0);
        weight = Math.min(0.22, weight);
        return lambda * (1.0 - weight) + target * weight;
    }

    static double h2hScoreWeight(int homeGoals, int awayGoals, H2hStats h2h) {
        if (h2h == null || h2h.overallGames() < 2) {
            return 1.0;
        }
        double outcomeProb;
        if (homeGoals > awayGoals) {
            outcomeProb = h2h.blendedHomeWinRate();
        } else if (homeGoals < awayGoals) {
            outcomeProb = h2h.blendedAwayWinRate();
        } else {
            outcomeProb = h2h.blendedDrawRate();
        }
        double strength = 0.08 * Math.min(1.0, h2h.overallGames() / 6.0)
                + 0.07 * Math.min(1.0, h2h.venueGames() / 4.0);
        return 0.92 + outcomeProb * (0.16 + strength);
    }

    private static void appendH2hLines(List<String> lines, H2hStats h2h, String homeCode) {
        if (h2h == null || h2h.overallGames() <= 0) {
            return;
        }
        lines.add(String.format(
                Locale.ROOT,
                "Личные встречи (%d): хозяева %d побед, %d ничьих, гости %d; ср. голы %.1f:%.1f",
                h2h.overallGames(),
                h2h.currentHomeWinsOverall(),
                h2h.drawsOverall(),
                h2h.currentAwayWinsOverall(),
                h2h.avgGoalsCurrentHomeOverall(),
                h2h.avgGoalsCurrentAwayOverall()
        ));
        if (h2h.venueGames() > 0) {
            lines.add(String.format(
                    Locale.ROOT,
                    "Дома у %s против этого соперника (%d): %d-%d-%d; ср. голы %.1f:%.1f",
                    homeCode,
                    h2h.venueGames(),
                    h2h.currentHomeWinsAtVenue(),
                    h2h.drawsAtVenue(),
                    h2h.currentAwayWinsAtVenue(),
                    h2h.avgGoalsCurrentHomeAtVenue(),
                    h2h.avgGoalsCurrentAwayAtVenue()
            ));
        } else {
            lines.add("Дома у " + homeCode + " против этого соперника ещё не играли в выборке");
        }
    }

    private static double soccerStatsScoreWeight(
            int homeGoals,
            int awayGoals,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            double thinSample
    ) {
        double weight = 1.0;
        double scale = 1.0 - 0.60 * thinSample;
        boolean homeWin = homeGoals > awayGoals;
        boolean awayWin = awayGoals > homeGoals;
        boolean draw = homeGoals == awayGoals;
        int margin = Math.abs(homeGoals - awayGoals);

        Double homeSf = home.ssScoredFirstPct() != null ? home.ssScoredFirstPct() : home.ssOgsPct();
        Double awaySf = away.ssScoredFirstPct() != null ? away.ssScoredFirstPct() : away.ssOgsPct();
        if (homeSf != null && homeWin) {
            weight *= 1.0 + ((homeSf / 100.0) - 0.45) * 0.10 * scale;
        }
        if (awaySf != null && awayWin) {
            weight *= 1.0 + ((awaySf / 100.0) - 0.45) * 0.10 * scale;
        }

        if (home.ssLeadPct() != null && homeWin) {
            weight *= 1.0 + ((home.ssLeadPct() / 100.0) - 0.35) * 0.08 * scale;
        }
        if (away.ssLeadPct() != null && awayWin) {
            weight *= 1.0 + ((away.ssLeadPct() / 100.0) - 0.35) * 0.08 * scale;
        }
        if (home.ssTrailPct() != null && awayWin) {
            weight *= 1.0 + ((home.ssTrailPct() / 100.0) - 0.30) * 0.05 * scale;
        }
        if (away.ssTrailPct() != null && homeWin) {
            weight *= 1.0 + ((away.ssTrailPct() / 100.0) - 0.30) * 0.05 * scale;
        }

        Double eqScored = avgNullable(home.ssEqualiserScoredPct(), away.ssEqualiserScoredPct());
        Double eqConceded = avgNullable(home.ssEqualiserConcededPct(), away.ssEqualiserConcededPct());
        if (draw && homeGoals > 0 && eqScored != null) {
            weight *= 1.0 + ((eqScored / 100.0) - 0.25) * 0.10 * scale;
        }
        if (margin >= 2 && eqConceded != null) {
            // High equaliser-conceded rate → large winning margins less sticky.
            weight *= 1.0 - ((eqConceded / 100.0) - 0.25) * 0.08 * scale;
        }
        if (draw && eqConceded != null) {
            weight *= 1.0 + ((eqConceded / 100.0) - 0.25) * 0.06 * scale;
        }

        Double sfPpg = avgNullable(home.ssScoredFirstPpg(), away.ssConcededFirstPpg());
        if (homeWin && home.ssScoredFirstPpg() != null && home.ssScoredFirstPpg() >= 2.2) {
            weight *= 1.0 + 0.04 * scale;
        }
        if (awayWin && away.ssScoredFirstPpg() != null && away.ssScoredFirstPpg() >= 2.2) {
            weight *= 1.0 + 0.04 * scale;
        }
        if (sfPpg != null && homeGoals + awayGoals <= 1 && sfPpg >= 2.4) {
            // Teams that convert first-goal situations into points still often keep matches low-ish.
            weight *= 1.0 + 0.02 * scale;
        }

        return Math.max(0.20, weight);
    }

    private static void appendSoccerStatsLines(
            List<String> lines,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            String homeCode,
            String awayCode
    ) {
        boolean any = home.ssScoredFirstPct() != null || home.ssLeadPct() != null
                || home.ssEqualiserScoredPct() != null || home.ssOgsPct() != null
                || away.ssScoredFirstPct() != null || away.ssLeadPct() != null;
        if (!any) {
            return;
        }
        lines.add(String.format(
                Locale.US,
                "Часто открывают счёт: %s %.0f%%, %s %.0f%%; ведут в матче %.0f%% / %.0f%%",
                homeCode,
                nz(home.ssScoredFirstPct(), home.ssOgsPct()),
                awayCode,
                nz(away.ssScoredFirstPct(), away.ssOgsPct()),
                nz(home.ssLeadPct()),
                nz(away.ssLeadPct())
        ));
        if (home.ssEqualiserScoredPct() != null || away.ssEqualiserConcededPct() != null
                || home.ssFavouritePpg() != null) {
            lines.add(String.format(
                    Locale.US,
                    "Отыгрыши: %s забивают/пропускают %.0f%% / %.0f%%, %s — %.0f%% / %.0f%%",
                    homeCode,
                    nz(home.ssEqualiserScoredPct()),
                    nz(home.ssEqualiserConcededPct()),
                    awayCode,
                    nz(away.ssEqualiserScoredPct()),
                    nz(away.ssEqualiserConcededPct())
            ));
        }
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
            FootyStatsTeamSnapshot homeSnap,
            FootyStatsTeamSnapshot awaySnap,
            FootyStatsExtendedMetrics home,
            FootyStatsExtendedMetrics away,
            double thinSample
    ) {
        double weight = 1.0;
        // Early-season 0% BTTS/CS/Over from 0–1 venue matches otherwise collapses every tip to 1:0.
        if (thinSample >= 0.55) {
            return weight;
        }
        int total = homeGoals + awayGoals;
        double sampleScale = 1.0 - 0.65 * thinSample;
        boolean homeVenue = hasVenueSample(homeSnap.scoredHome(), homeSnap.concededHome());
        boolean awayVenue = hasVenueSample(awaySnap.scoredAway(), awaySnap.concededAway());

        if (homeGoals == 0 && homeVenue) {
            weight *= 1.0 + (cleanSheetBoost(home.formCsHome(), home.seasonCsHome()) - 1.0) * sampleScale;
        }
        if (homeGoals == 0 && awayVenue) {
            weight *= 1.0 + (failedToScoreBoost(away.ftsAway()) - 1.0) * sampleScale;
        }
        if (awayGoals == 0 && awayVenue) {
            weight *= 1.0 + (cleanSheetBoost(away.formCsAway(), away.seasonCsAway()) - 1.0) * sampleScale;
        }
        if (awayGoals == 0 && homeVenue) {
            weight *= 1.0 + (failedToScoreBoost(home.ftsHome()) - 1.0) * sampleScale;
        }

        Double btts = avgNullable(
                homeVenue ? home.formBttsHome() : null,
                homeVenue ? home.seasonBttsHome() : null,
                awayVenue ? away.formBttsAway() : null,
                awayVenue ? away.seasonBttsAway() : null
        );
        if (btts != null) {
            double ratio = btts / 100.0;
            double factor;
            if (homeGoals > 0 && awayGoals > 0) {
                factor = 0.90 + ratio * 0.20;
            } else if (homeGoals == 0 || awayGoals == 0) {
                factor = 1.10 - ratio * 0.18;
            } else {
                factor = 1.0;
            }
            weight *= 1.0 + (factor - 1.0) * sampleScale;
        }

        if (homeGoals == awayGoals && homeGoals > 0) {
            Double draw = avgNullable(
                    homeVenue ? home.drawPctHome() : null,
                    awayVenue ? away.drawPctAway() : null,
                    home.drawPctOverall()
            );
            if (draw != null) {
                double factor = 0.92 + (draw / 100.0) * 0.16;
                weight *= 1.0 + (factor - 1.0) * sampleScale;
            }
        }

        Double over25 = avgNullable(
                homeVenue ? home.over25Home() : null,
                awayVenue ? away.over25Away() : null,
                homeVenue || awayVenue ? home.over25Overall() : null
        );
        Double under25 = avgNullable(
                homeVenue ? home.under25Home() : null,
                awayVenue ? away.under25Away() : null,
                homeVenue || awayVenue ? home.under25Overall() : null
        );
        if (total >= 3 && over25 != null) {
            double factor = 0.90 + (over25 / 100.0) * 0.20;
            weight *= 1.0 + (factor - 1.0) * sampleScale;
        }
        if (total <= 1 && under25 != null) {
            double factor = 0.90 + (under25 / 100.0) * 0.18;
            weight *= 1.0 + (factor - 1.0) * sampleScale;
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

    private static void appendFormLines(
            List<String> lines,
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsExtendedMetrics homeExt,
            FootyStatsExtendedMetrics awayExt
    ) {
        if (homeExt.formBttsHome() == null && awayExt.formBttsAway() == null) {
            return;
        }
        String homeBtts = hasVenueSample(home.scoredHome(), home.concededHome())
                ? String.format(Locale.US, "%.0f%%", nz(homeExt.formBttsHome()))
                : "нет матчей";
        String awayBtts = hasVenueSample(away.scoredAway(), away.concededAway())
                ? String.format(Locale.US, "%.0f%%", nz(awayExt.formBttsAway()))
                : "нет матчей";
        String homeCs = hasVenueSample(home.scoredHome(), home.concededHome())
                ? String.format(Locale.US, "%.0f%%", nz(homeExt.formCsHome()))
                : "—";
        String awayCs = hasVenueSample(away.scoredAway(), away.concededAway())
                ? String.format(Locale.US, "%.0f%%", nz(awayExt.formCsAway()))
                : "—";
        lines.add("Форма: оба забьют дома " + homeBtts + ", в гостях — " + awayBtts
                + "; сухие " + homeCs + " / " + awayCs);
    }

    private static void appendSeasonLines(
            List<String> lines,
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsExtendedMetrics homeExt,
            FootyStatsExtendedMetrics awayExt,
            String homeCode,
            String awayCode
    ) {
        if (homeExt.seasonScoredHome() == null && awayExt.seasonScoredAway() == null
                && homeExt.over25Home() == null
                && !hasVenueSample(home.scoredHome(), home.concededHome())
                && !hasVenueSample(away.scoredAway(), away.concededAway())) {
            return;
        }
        lines.add(String.format(
                Locale.ROOT,
                "Сезон: %s, %s%s",
                venueScoredPhrase(homeCode, "дома", home.scoredHome(), home.concededHome(), homeExt.seasonScoredHome()),
                venueScoredPhrase(awayCode, "в гостях", away.scoredAway(), away.concededAway(), awayExt.seasonScoredAway()),
                over25Phrase(home, away, homeExt, awayExt)
        ));
    }

    static String venueScoredPhrase(
            String code,
            String place,
            double formScored,
            double formConceded,
            Double seasonVenue
    ) {
        if (!hasVenueSample(formScored, formConceded)) {
            return code + " " + place + " ещё не играли";
        }
        double rate = seasonVenue != null ? seasonVenue : formScored;
        return String.format(Locale.US, "%s %s забивает %.1f", code, place, rate);
    }

    private static String over25Phrase(
            FootyStatsTeamSnapshot home,
            FootyStatsTeamSnapshot away,
            FootyStatsExtendedMetrics homeExt,
            FootyStatsExtendedMetrics awayExt
    ) {
        boolean homeOk = hasVenueSample(home.scoredHome(), home.concededHome()) && homeExt.over25Home() != null;
        boolean awayOk = hasVenueSample(away.scoredAway(), away.concededAway()) && awayExt.over25Away() != null;
        if (!homeOk && !awayOk) {
            return "";
        }
        String homePart = homeOk ? String.format(Locale.US, "%.0f%%", homeExt.over25Home()) : "—";
        String awayPart = awayOk ? String.format(Locale.US, "%.0f%%", awayExt.over25Away()) : "—";
        return "; тотал больше 2.5 — " + homePart + " / " + awayPart;
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
