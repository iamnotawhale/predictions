package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.recommender.PoissonScoreModel.MarketOutcome;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.ScoreDistribution;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

final class RecommendationAnalysisWriter {

    private RecommendationAnalysisWriter() {
    }

    static String write(
            String homeCode,
            String awayCode,
            int tipHome,
            int tipAway,
            double tipProb,
            double expectedPoints,
            int mostLikelyHome,
            int mostLikelyAway,
            double mostLikelyProb,
            double lambdaHome,
            double lambdaAway,
            ScoreDistribution distribution,
            MarketOutcome market,
            TeamStatsSnapshot home,
            TeamStatsSnapshot away,
            ExtendedTeamMetrics homeExt,
            ExtendedTeamMetrics awayExt,
            H2hStats h2h,
            double thinSample
    ) {
        List<String> paragraphs = new ArrayList<>(4);
        paragraphs.add(hookParagraph(
                homeCode, awayCode, tipHome, tipAway, tipProb, expectedPoints,
                mostLikelyHome, mostLikelyAway, mostLikelyProb
        ));
        paragraphs.add(leanParagraph(homeCode, awayCode, lambdaHome, lambdaAway, distribution, market));
        String contrast = contrastParagraph(homeCode, awayCode, home, away, homeExt, awayExt, thinSample);
        if (contrast != null) {
            paragraphs.add(contrast);
        }
        String h2hLine = h2hParagraph(homeCode, awayCode, h2h);
        String markets = marketsCallParagraph(tipHome, tipAway, distribution);
        if (h2hLine != null) {
            paragraphs.add(h2hLine + " " + markets);
        } else {
            paragraphs.add(markets);
        }
        return String.join("\n\n", paragraphs);
    }

    private static String hookParagraph(
            String homeCode,
            String awayCode,
            int tipHome,
            int tipAway,
            double tipProb,
            double expectedPoints,
            int mostLikelyHome,
            int mostLikelyAway,
            double mostLikelyProb
    ) {
        String tip = tipHome + ":" + tipAway;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                Locale.US,
                "Для очков в игре рекомендуем %s (около %.0f%%, ожидание ≈ %.2f очка).",
                tip,
                tipProb * 100,
                expectedPoints
        ));
        if (mostLikelyHome != tipHome || mostLikelyAway != tipAway) {
            sb.append(String.format(
                    Locale.US,
                    " Самый вероятный счёт — %d:%d (≈ %.0f%%), но под правила EPL выгоднее %s.",
                    mostLikelyHome,
                    mostLikelyAway,
                    mostLikelyProb * 100,
                    tip
            ));
        } else {
            sb.append(String.format(
                    Locale.US,
                    " Это же и самый вероятный исход для %s — %s.",
                    homeCode + "–" + awayCode,
                    tip
            ));
        }
        return sb.toString();
    }

    private static String leanParagraph(
            String homeCode,
            String awayCode,
            double lambdaHome,
            double lambdaAway,
            ScoreDistribution distribution,
            MarketOutcome market
    ) {
        double pHome = distribution != null ? distribution.homeWin() : (market != null ? market.homeWin() : 0);
        double pDraw = distribution != null ? distribution.draw() : (market != null ? market.draw() : 0);
        double pAway = distribution != null ? distribution.awayWin() : (market != null ? market.awayWin() : 0);
        String lean;
        if (pHome >= pDraw && pHome >= pAway) {
            lean = String.format(Locale.US, "%s к хозяевам (%s, ≈ %.0f%%)", leanStrength(pHome), homeCode, pHome * 100);
        } else if (pAway >= pDraw && pAway >= pHome) {
            lean = String.format(Locale.US, "%s к гостям (%s, ≈ %.0f%%)", leanStrength(pAway), awayCode, pAway * 100);
        } else {
            lean = String.format(Locale.US, "ничья в центре (≈ %.0f%%)", pDraw * 100);
        }
        String base = String.format(
                Locale.US,
                "Модель даёт %.2f ожидаемых голов у %s против %.2f у %s — %s.",
                lambdaHome,
                homeCode,
                lambdaAway,
                awayCode,
                lean
        );
        if (market != null) {
            return base + " Исход 1X2 опирается на рынок; статистика лишь уточняет форму счёта.";
        }
        return base;
    }

    private static String leanStrength(double leadProb) {
        if (leadProb >= 0.55) {
            return "явный уклон";
        }
        if (leadProb >= 0.42) {
            return "уклон";
        }
        return "лёгкий уклон";
    }

    private static String contrastParagraph(
            String homeCode,
            String awayCode,
            TeamStatsSnapshot home,
            TeamStatsSnapshot away,
            ExtendedTeamMetrics homeExt,
            ExtendedTeamMetrics awayExt,
            double thinSample
    ) {
        List<String> bits = new ArrayList<>();
        if (thinSample >= 0.55) {
            bits.add("Выборка ещё тонкая — вес формы снижен");
        }
        Double homeXg = home != null ? home.xgOverall() : null;
        Double awayXg = away != null ? away.xgOverall() : null;
        Double homeXga = home != null ? home.xgaOverall() : null;
        Double awayXga = away != null ? away.xgaOverall() : null;
        if (homeXg != null && awayXg != null && homeXga != null && awayXga != null) {
            bits.add(String.format(
                    Locale.US,
                    "%s в среднем %.1f xG / %.1f xGA за матч, %s — %.1f / %.1f",
                    homeCode, homeXg, homeXga, awayCode, awayXg, awayXga
            ));
        }
        Double homeRecent = homeExt != null ? homeExt.recentXgPerMatch() : null;
        Double awayRecent = awayExt != null ? awayExt.recentXgPerMatch() : null;
        if (homeRecent != null && awayRecent != null) {
            bits.add(String.format(
                    Locale.US,
                    "в последних матчах с xG: %.1f против %.1f",
                    homeRecent,
                    awayRecent
            ));
        }
        String xPtsBit = xPtsBit(homeCode, homeExt);
        if (xPtsBit != null) {
            bits.add(xPtsBit);
        }
        String xPtsAway = xPtsBit(awayCode, awayExt);
        if (xPtsAway != null) {
            bits.add(xPtsAway);
        }
        if (bits.isEmpty()) {
            return null;
        }
        return "Контекст: " + String.join("; ", bits) + ".";
    }

    private static String xPtsBit(String code, ExtendedTeamMetrics ext) {
        if (ext == null || ext.xPtsDelta() == null) {
            return null;
        }
        double d = ext.xPtsDelta();
        if (Math.abs(d) < 1.5) {
            return null;
        }
        if (d < 0) {
            return String.format(Locale.US, "%s недобирает очки относительно xPts (Δ %.1f)", code, d);
        }
        return String.format(Locale.US, "%s набирает больше xPts (Δ +%.1f)", code, d);
    }

    private static String h2hParagraph(String homeCode, String awayCode, H2hStats h2h) {
        if (h2h == null || h2h.overallGames() < 2) {
            return null;
        }
        return String.format(
                Locale.US,
                "В личных встречах (%d): %s %dW–%dD–%dL против %s.",
                h2h.overallGames(),
                homeCode,
                h2h.currentHomeWinsOverall(),
                h2h.drawsOverall(),
                h2h.currentAwayWinsOverall(),
                awayCode
        );
    }

    private static String marketsCallParagraph(int tipHome, int tipAway, ScoreDistribution distribution) {
        String tip = tipHome + ":" + tipAway;
        if (distribution == null) {
            return "Итоговый call: " + tip + ".";
        }
        return String.format(
                Locale.US,
                "Тотал больше 2.5 ≈ %.0f%%, обе забьют ≈ %.0f%%. Итоговый call: %s.",
                distribution.over25() * 100,
                distribution.btts() * 100,
                tip
        );
    }
}
