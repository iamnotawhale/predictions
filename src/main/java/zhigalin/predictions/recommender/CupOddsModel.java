package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

public final class CupOddsModel {

    private static final int MAX_GOALS = 6;
    private static final double DEFAULT_TOTAL = 2.6;
    private static final double LEAGUE_GOALS = 1.35;
    private static final double SHRINK_K = 4.0;
    private static final double FORM_WEIGHT = 0.15;
    private static final double H2H_WEIGHT = 0.05;
    private static final int MIN_FORM_MATCHES = 3;

    public record Odds(Double home, Double draw, Double away, Double overLine, Double over, Double under) {
    }

    public record TeamForm(double goalsFor, double goalsAgainst, int matches, String league) {
    }

    public record HeadToHead(double homeGoals, double awayGoals, int matches) {
    }

    public record Form(TeamForm home, TeamForm away, HeadToHead h2h) {
    }

    public record Tip(int home, int away, double expectedPoints, double pHome, double pDraw, double pAway,
                      boolean formApplied) {
    }

    private CupOddsModel() {
    }

    public static Optional<Odds> fromEspnSummary(JsonNode root) {
        JsonNode odds = root == null ? null : root.path("odds");
        if (odds == null || !odds.isArray() || odds.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = odds.get(0);
        return Optional.of(new Odds(
                moneyLine(first.path("homeTeamOdds")),
                moneyLine(first.path("drawOdds")),
                moneyLine(first.path("awayTeamOdds")),
                number(first, "overUnder"),
                number(first, "overOdds"),
                number(first, "underOdds")));
    }

    public static Form formFromEspnSummary(JsonNode root, Instant kickoff) {
        JsonNode competitors = root == null ? null : root.path("header").path("competitions").path(0).path("competitors");
        if (competitors == null || !competitors.isArray()) {
            return new Form(null, null, null);
        }
        String homeId = null;
        String awayId = null;
        for (JsonNode c : competitors) {
            if ("home".equals(text(c, "homeAway"))) {
                homeId = text(c, "id");
            } else if ("away".equals(text(c, "homeAway"))) {
                awayId = text(c, "id");
            }
        }
        if (homeId == null || awayId == null) {
            return new Form(null, null, null);
        }
        TeamForm home = teamForm(root.path("lastFiveGames"), homeId, kickoff);
        TeamForm away = teamForm(root.path("lastFiveGames"), awayId, kickoff);
        if (home != null && away != null && !home.league().equals(away.league())) {
            home = null;
            away = null;
        }
        return new Form(home, away, headToHead(root.path("seasonseries"), homeId, awayId, kickoff));
    }

    public static Optional<Tip> recommend(Odds odds) {
        return recommend(odds, null);
    }

    public static Optional<Tip> recommend(Odds odds, Form form) {
        if (odds == null || odds.home() == null || odds.draw() == null || odds.away() == null) {
            return Optional.empty();
        }
        PoissonScoreModel.MarketOutcome market = market(odds);
        double[][] matrix = scoreMatrix(market, totalTarget(odds), form);
        return Optional.of(bestByCupPoints(matrix, market, formShare(form).isPresent()));
    }

    static PoissonScoreModel.MarketOutcome market(Odds odds) {
        double home = implied(odds.home());
        double draw = implied(odds.draw());
        double away = implied(odds.away());
        double sum = home + draw + away;
        return new PoissonScoreModel.MarketOutcome(home / sum, draw / sum, away / sum);
    }

    static double totalTarget(Odds odds) {
        if (odds.overLine() == null || odds.over() == null || odds.under() == null) {
            return DEFAULT_TOTAL;
        }
        double over = implied(odds.over());
        double under = implied(odds.under());
        return MarketScoreMatrix.totalMeanForOver(odds.overLine(), over / (over + under));
    }

    static double[][] scoreMatrix(PoissonScoreModel.MarketOutcome market, double totalTarget, Form form) {
        MarketScoreMatrix.Lambdas fitted = MarketScoreMatrix.fit(market, totalTarget);
        double total = fitted.home() + fitted.away();
        double share = fitted.home() / total;
        Optional<Double> formShare = formShare(form);
        Optional<Double> h2hShare = h2hShare(form);
        if (formShare.isPresent()) {
            share = share * (1 - FORM_WEIGHT) + FORM_WEIGHT * formShare.get();
        }
        if (h2hShare.isPresent()) {
            share = share * (1 - H2H_WEIGHT) + H2H_WEIGHT * h2hShare.get();
        }
        double home = total * share;
        return MarketScoreMatrix.calibrateTo1x2(
                MarketScoreMatrix.poisson(home, total - home, MAX_GOALS), market);
    }

    static Optional<Double> formShare(Form form) {
        if (form == null || form.home() == null || form.away() == null) {
            return Optional.empty();
        }
        double homeAttack = shrunk(form.home().goalsFor(), form.home().matches()) / LEAGUE_GOALS;
        double homeConceded = shrunk(form.home().goalsAgainst(), form.home().matches()) / LEAGUE_GOALS;
        double awayAttack = shrunk(form.away().goalsFor(), form.away().matches()) / LEAGUE_GOALS;
        double awayConceded = shrunk(form.away().goalsAgainst(), form.away().matches()) / LEAGUE_GOALS;
        double homeExpected = LEAGUE_GOALS * homeAttack * awayConceded;
        double awayExpected = LEAGUE_GOALS * awayAttack * homeConceded;
        return Optional.of(homeExpected / (homeExpected + awayExpected));
    }

    static Optional<Double> h2hShare(Form form) {
        if (form == null || form.h2h() == null || form.h2h().matches() <= 0) {
            return Optional.empty();
        }
        double home = form.h2h().homeGoals();
        double away = form.h2h().awayGoals();
        return Optional.of((home + 1) / (home + away + 2));
    }

    private static double shrunk(double goals, int matches) {
        return (goals + SHRINK_K * LEAGUE_GOALS) / (matches + SHRINK_K);
    }

    static Tip bestByCupPoints(double[][] matrix, PoissonScoreModel.MarketOutcome market, boolean formApplied) {
        int bestHome = 0;
        int bestAway = 0;
        double bestEv = -1;
        for (int h = 0; h < matrix.length; h++) {
            for (int a = 0; a < matrix.length; a++) {
                double ev = expectedCupPoints(matrix, h, a);
                if (ev > bestEv + 1e-12) {
                    bestEv = ev;
                    bestHome = h;
                    bestAway = a;
                }
            }
        }
        return new Tip(bestHome, bestAway, bestEv, market.homeWin(), market.draw(), market.awayWin(), formApplied);
    }

    static double expectedCupPoints(double[][] matrix, int h, int a) {
        double ev = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                ev += matrix[i][j] * PredictionService.computePoints(ScoringMode.CUP, i, j, h, a);
            }
        }
        return ev;
    }

    static double implied(double american) {
        return american > 0 ? 100.0 / (american + 100.0) : -american / (-american + 100.0);
    }

    private static TeamForm teamForm(JsonNode lastFive, String teamId, Instant kickoff) {
        double goalsFor = 0;
        double goalsAgainst = 0;
        int matches = 0;
        Map<String, Integer> leagues = new HashMap<>();
        String mainLeague = null;
        for (JsonNode entry : lastFive) {
            if (!teamId.equals(entry.path("team").path("id").asText())) {
                continue;
            }
            for (JsonNode event : entry.path("events")) {
                Integer home = score(text(event, "homeTeamScore"));
                Integer away = score(text(event, "awayTeamScore"));
                if (home == null || away == null || !isRegulationResult(event) || !before(text(event, "gameDate"), kickoff)) {
                    continue;
                }
                boolean isHome = teamId.equals(event.path("homeTeamId").asText());
                goalsFor += isHome ? home : away;
                goalsAgainst += isHome ? away : home;
                matches++;
                String league = text(event, "leagueAbbreviation");
                if (league != null) {
                    leagues.merge(league, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> e : leagues.entrySet()) {
            if (mainLeague == null || e.getValue() > leagues.get(mainLeague)) {
                mainLeague = e.getKey();
            }
        }
        return matches >= MIN_FORM_MATCHES && mainLeague != null
                ? new TeamForm(goalsFor, goalsAgainst, matches, mainLeague)
                : null;
    }

    private static HeadToHead headToHead(JsonNode seasonSeries, String homeId, String awayId, Instant kickoff) {
        if (!seasonSeries.isArray() || seasonSeries.isEmpty()) {
            return null;
        }
        double homeGoals = 0;
        double awayGoals = 0;
        int matches = 0;
        for (JsonNode event : seasonSeries.get(0).path("events")) {
            if (!event.path("statusType").path("completed").asBoolean(false)
                    || !before(text(event, "date"), kickoff)) {
                continue;
            }
            Integer home = null;
            Integer away = null;
            for (JsonNode c : event.path("competitors")) {
                Integer value = score(text(c, "score"));
                if (homeId.equals(c.path("team").path("id").asText())) {
                    home = value;
                } else if (awayId.equals(c.path("team").path("id").asText())) {
                    away = value;
                }
            }
            if (home == null || away == null) {
                continue;
            }
            homeGoals += home;
            awayGoals += away;
            matches++;
        }
        return matches > 0 ? new HeadToHead(homeGoals, awayGoals, matches) : null;
    }

    private static boolean isRegulationResult(JsonNode event) {
        if (intValue(event, "homeShootoutScore") > 0 || intValue(event, "awayShootoutScore") > 0) {
            return false;
        }
        Integer homeAggregate = score(text(event, "homeAggregateScore"));
        Integer awayAggregate = score(text(event, "awayAggregateScore"));
        if (homeAggregate == null || awayAggregate == null || (homeAggregate == 0 && awayAggregate == 0)) {
            return true;
        }
        return homeAggregate.equals(score(text(event, "homeTeamScore")))
                && awayAggregate.equals(score(text(event, "awayTeamScore")));
    }

    private static int intValue(JsonNode node, String field) {
        Integer value = score(text(node, field));
        return value == null ? 0 : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean before(String isoDate, Instant kickoff) {
        if (isoDate == null || isoDate.isBlank()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(isoDate).toInstant().isBefore(kickoff);
        } catch (Exception e) {
            return false;
        }
    }

    private static Integer score(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double moneyLine(JsonNode node) {
        return number(node, "moneyLine");
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }
}
