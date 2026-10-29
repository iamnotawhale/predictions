package zhigalin.predictions.service.predict;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileBreakdownRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileHabitRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileHighlight;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileStatsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamPredictionItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamPredictionsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamQualityRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileWeekHighlight;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.recommender.FootyStatsStatsDao;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.api.TeamLogoCacheService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.util.DaoUtil;

/**
 * Personal prediction stats for the Profile screen (no cross-user comparisons).
 */
@Service
public class UserPredictionStatsService {

    private final PredictionService predictionService;
    private final MatchService matchService;
    private final CupMatchDao cupMatchDao;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final TeamLogoCacheService teamLogoCacheService;
    private final FootyStatsStatsDao footyStatsStatsDao;

    private static final java.time.format.DateTimeFormatter KICKOFF_DAY =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM");

    public UserPredictionStatsService(
            PredictionService predictionService,
            MatchService matchService,
            CupMatchDao cupMatchDao,
            UserWeekBonusMatchService userWeekBonusMatchService,
            TeamLogoCacheService teamLogoCacheService,
            FootyStatsStatsDao footyStatsStatsDao
    ) {
        this.predictionService = predictionService;
        this.matchService = matchService;
        this.cupMatchDao = cupMatchDao;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.teamLogoCacheService = teamLogoCacheService;
        this.footyStatsStatsDao = footyStatsStatsDao;
    }

    public ProfileTeamPredictionsResponse teamPredictions(User user, Team team) {
        int userId = user.getId();
        Map<Integer, Prediction> eplPicks = new HashMap<>();
        for (MatchPrediction mp : predictionService.findAllByUserId(userId)) {
            if (mp.match() != null && mp.prediction() != null) {
                eplPicks.put(mp.match().getPublicId(), mp.prediction());
            }
        }
        Map<Integer, Prediction> cupPicks = new HashMap<>();
        for (Prediction p : predictionService.findAllCupByUserId(userId)) {
            cupPicks.put(p.getCupMatchId(), p);
        }

        List<Fixture> fixtures = new ArrayList<>();
        matchService.findAllByTeamPublicId(team.getPublicId()).forEach(m -> fixtures.add(Fixture.of(m)));
        cupMatchDao.findAllByTeamPublicId(team.getPublicId()).forEach(c -> fixtures.add(Fixture.of(c)));

        List<TeamRow> rows = new ArrayList<>();
        for (Fixture f : fixtures) {
            Prediction pred = f.cup() ? cupPicks.get(f.publicId()) : eplPicks.get(f.publicId());
            if (!isFinished(f.status()) && pred == null) {
                continue;
            }
            boolean weekBonus = weekBonus(userId, f);
            Integer pts = null;
            if (pred != null && isFinished(f.status())
                    && classify(f.homeScore(), f.awayScore(), pred.getHomeTeamScore(), pred.getAwayTeamScore()) != Bucket.NO_BET) {
                pts = points(f, pred, weekBonus);
            }
            rows.add(new TeamRow(f, pred, pts, weekBonus));
        }
        Comparator<LocalDateTime> soonestFirst = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<TeamRow> byKickoff = (a, b) -> isFinished(a.fixture().status())
                ? soonestFirst.reversed().compare(a.fixture().kickoff(), b.fixture().kickoff())
                : soonestFirst.compare(a.fixture().kickoff(), b.fixture().kickoff());
        rows.sort(Comparator.comparing((TeamRow r) -> isFinished(r.fixture().status())).thenComparing(byKickoff));

        List<Integer> scoredEpl = rows.stream()
                .filter(r -> r.points() != null && !r.fixture().cup())
                .map(r -> r.fixture().publicId()).toList();
        List<Integer> scoredCup = rows.stream()
                .filter(r -> r.points() != null && r.fixture().cup())
                .map(r -> r.fixture().publicId()).toList();
        Map<Integer, int[]> aiTips = footyStatsStatsDao.findKickoffScoresByMatchIds(
                fixtures.stream().filter(f -> !f.cup()).map(Fixture::publicId).toList());
        Map<Integer, int[]> others = predictionService.othersPointsByMatchIds(scoredEpl, userId);
        Map<Integer, int[]> cupOthers = predictionService.othersPointsByCupMatchIds(scoredCup, userId);

        List<ProfileTeamPredictionItem> items = new ArrayList<>();
        int pointsSum = 0;
        int pointsN = 0;
        int aiSum = 0;
        int aiN = 0;
        int othersSum = 0;
        int othersN = 0;
        for (TeamRow row : rows) {
            Fixture f = row.fixture();
            Prediction pred = row.prediction();
            Integer pts = row.points();
            if (pts != null) {
                pointsSum += pts;
                pointsN++;
            }
            int[] ai = f.cup() ? null : aiTips.get(f.publicId());
            Integer aiPts = null;
            if (ai != null && pts != null) {
                aiPts = PredictionService.computePoints(ScoringMode.EPL, f.homeScore(), f.awayScore(), ai[0], ai[1]);
                aiSum += aiPts;
                aiN++;
            }
            int[] o = pts == null ? null : (f.cup() ? cupOthers : others).get(f.publicId());
            Double othersAvg = null;
            int othersCount = 0;
            if (o != null && o[1] > 0) {
                othersAvg = round1(o[0] / (double) o[1]);
                othersCount = o[1];
                othersSum += o[0];
                othersN += o[1];
            }
            boolean finished = isFinished(f.status());
            items.add(new ProfileTeamPredictionItem(
                    f.publicId(),
                    side(f.homeTeamId(), f.homeName()),
                    side(f.awayTeamId(), f.awayName()),
                    f.kickoff() != null ? f.kickoff().format(KICKOFF_DAY) : null,
                    finished,
                    row.weekBonus(),
                    f.cup(),
                    f.competition(),
                    finished ? f.homeScore() : null,
                    finished ? f.awayScore() : null,
                    pred != null ? pred.getHomeTeamScore() : null,
                    pred != null ? pred.getAwayTeamScore() : null,
                    pts,
                    ai != null ? ai[0] : null,
                    ai != null ? ai[1] : null,
                    aiPts,
                    othersAvg,
                    othersCount
            ));
        }
        return new ProfileTeamPredictionsResponse(
                team.getCode(),
                team.getName() != null ? team.getName() : team.getCode(),
                teamLogoCacheService.pathForTeam(team.getPublicId()),
                pointsN,
                pointsN == 0 ? null : round1(pointsSum / (double) pointsN),
                aiN,
                aiN == 0 ? null : round1(aiSum / (double) aiN),
                othersN == 0 ? null : round1(othersSum / (double) othersN),
                items
        );
    }

    public ProfileStatsResponse build(User user) {
        int userId = user.getId();
        int currentWeekId = DaoUtil.currentWeekId;
        List<UserPick> picks = userPicks(userId);

        int seasonPoints = predictionService.getAllPointsByUsers()
                .getOrDefault(user.getLogin(), 0);
        int currentWeekPoints = predictionService.getWeeklyUsersPoints(currentWeekId)
                .getOrDefault(user.getLogin(), 0);

        String bonusLabel = null;
        Integer weekBonusMatchId = userWeekBonusMatchService.findAssigned(userId, currentWeekId).orElse(null);
        if (weekBonusMatchId != null) {
            Match bonusMatch = matchService.findByPublicId(weekBonusMatchId);
            if (bonusMatch != null) {
                String home = code(bonusMatch.getHomeTeamId());
                String away = code(bonusMatch.getAwayTeamId());
                if (home != null && away != null) {
                    bonusLabel = home + "–" + away;
                }
            }
            if (bonusLabel == null) {
                bonusLabel = picks.stream()
                        .filter(p -> !p.fixture().cup() && p.fixture().publicId() == weekBonusMatchId)
                        .findFirst()
                        .map(p -> code(p.fixture().homeTeamId()) + "–" + code(p.fixture().awayTeamId()))
                        .orElse(null);
            }
        }

        int exact = 0;
        int goalDiff = 0;
        int outcome = 0;
        int miss = 0;
        int finishedWithPick = 0;
        int pointsSum = 0;
        int pointsN = 0;

        Map<Integer, int[]> weekAgg = new HashMap<>(); // week -> [pointsSum, count]
        Map<String, int[]> scorelineCounts = new HashMap<>();
        Map<String, TeamAgg> byTeam = new HashMap<>();

        int pickHome = 0;
        int pickDraw = 0;
        int pickAway = 0;
        int pickBtts = 0;
        int pickOver25 = 0;
        int pickTotalGoalsSum = 0;
        int actualTotalGoalsSum = 0;
        int pickScored = 0;
        int ptsHomePickSum = 0;
        int ptsHomePickN = 0;
        int ptsDrawPickSum = 0;
        int ptsDrawPickN = 0;
        int ptsAwayPickSum = 0;
        int ptsAwayPickN = 0;

        int bonusMatches = 0;
        int bonusPointsSum = 0;
        int cupMatches = 0;
        int cupPointsSum = 0;

        for (UserPick pick : picks) {
            Fixture f = pick.fixture();
            Prediction pred = pick.prediction();
            if (!isFinished(f.status())) {
                continue;
            }
            Integer rh = f.homeScore();
            Integer ra = f.awayScore();
            Integer ph = pred.getHomeTeamScore();
            Integer pa = pred.getAwayTeamScore();

            Bucket bucket = classify(rh, ra, ph, pa);
            if (bucket == Bucket.NO_BET) {
                continue;
            }
            boolean weekBonus = weekBonus(userId, f);
            int pts = points(f, pred, weekBonus);

            switch (bucket) {
                case EXACT -> exact++;
                case GOAL_DIFF -> goalDiff++;
                case OUTCOME -> outcome++;
                case MISS -> miss++;
                case NO_BET -> {
                }
            }

            finishedWithPick++;
            pointsSum += pts;
            pointsN++;
            String line = ph + ":" + pa;
            scorelineCounts.merge(line, new int[]{1}, (a, b) -> {
                a[0] += b[0];
                return a;
            });
            pickScored++;
            pickTotalGoalsSum += ph + pa;
            actualTotalGoalsSum += rh + ra;
            if (ph > pa) {
                pickHome++;
                ptsHomePickSum += pts;
                ptsHomePickN++;
            } else if (Objects.equals(ph, pa)) {
                pickDraw++;
                ptsDrawPickSum += pts;
                ptsDrawPickN++;
            } else {
                pickAway++;
                ptsAwayPickSum += pts;
                ptsAwayPickN++;
            }
            if (ph > 0 && pa > 0) {
                pickBtts++;
            }
            if (ph + pa >= 3) {
                pickOver25++;
            }
            accumulateTeam(byTeam, f.homeTeamId(), pts);
            accumulateTeam(byTeam, f.awayTeamId(), pts);

            if (!f.cup()) {
                weekAgg.computeIfAbsent(f.weekId(), w -> new int[2]);
                int[] wa = weekAgg.get(f.weekId());
                wa[0] += pts;
                wa[1] += 1;
            }

            if (weekBonus) {
                bonusMatches++;
                bonusPointsSum += pts;
            }
            if (f.cup()) {
                cupMatches++;
                cupPointsSum += pts;
            }
        }

        int scoredOrMiss = exact + goalDiff + outcome + miss;
        double exactPct = pct(exact, scoredOrMiss);
        double hitPct = pct(exact + goalDiff + outcome, scoredOrMiss);
        double avgPts = pointsN > 0 ? round1(pointsSum / (double) pointsN) : 0;

        List<ProfileHighlight> highlights = List.of(
                new ProfileHighlight(
                        "Точный счёт",
                        formatPct(exactPct),
                        "Доля матчей, где угадан точный счёт (среди прогнозов с цифрами)."
                ),
                new ProfileHighlight(
                        "Исход",
                        formatPct(hitPct),
                        "Угадан победитель или ничья: точный счёт, разница или только исход."
                ),
                new ProfileHighlight(
                        "Ср. очки",
                        formatNum(avgPts),
                        "Средние очки за матч с прогнозом (АПЛ и кубки, с учётом бонуса тура)."
                )
        );

        List<ProfileBreakdownRow> breakdown = List.of(
                row("Точный счёт", exact, scoredOrMiss,
                        "Факт полностью совпал с прогнозом."),
                row("Разница голов", goalDiff, scoredOrMiss,
                        "Верная разница, но не точный счёт (например 2:0 вместо 3:1)."),
                row("Только исход", outcome, scoredOrMiss,
                        "Угадан победитель/ничья, но не разница голов."),
                row("Промах", miss, scoredOrMiss,
                        "Исход не угадан.")
        );

        String favoriteScore = scorelineCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> e.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .filter(e -> e.getValue()[0] >= 2)
                .map(Map.Entry::getKey)
                .orElse(null);

        List<ProfileHabitRow> habits = new ArrayList<>();
        if (pickScored > 0) {
            habits.add(new ProfileHabitRow(
                    "Чаще ставите на",
                    dominantPickLabel(pickHome, pickDraw, pickAway),
                    "Среди ваших прогнозов: победа хозяев / ничья / гости."
            ));
            if (ptsHomePickN > 0 || ptsDrawPickN > 0 || ptsAwayPickN > 0) {
                habits.add(new ProfileHabitRow(
                        "Ср. очки по типу ставки",
                        formatPickTypeAvgs(ptsHomePickSum, ptsHomePickN, ptsDrawPickSum, ptsDrawPickN, ptsAwayPickSum, ptsAwayPickN),
                        "Сколько в среднем приносят прогнозы «П1», «X» и «П2»."
                ));
            }
            double predTot = round1(pickTotalGoalsSum / (double) pickScored);
            double actTot = round1(actualTotalGoalsSum / (double) pickScored);
            double bias = round1(predTot - actTot);
            String biasSign = bias > 0 ? "+" : "";
            habits.add(new ProfileHabitRow(
                    "Тотал: прогноз / факт",
                    formatNum(predTot) + " / " + formatNum(actTot) + " (" + biasSign + formatNum(bias) + ")",
                    "Средняя сумма голов в вашем счёте против реального тотала. Плюс — завышаете тотал."
            ));
            habits.add(new ProfileHabitRow(
                    "Обе забьют",
                    formatPct(pct(pickBtts, pickScored)),
                    "Доля прогнозов, где обеим ставите хотя бы по голу."
            ));
            habits.add(new ProfileHabitRow(
                    "Тотал 2.5+",
                    formatPct(pct(pickOver25, pickScored)),
                    "Доля прогнозов с суммой голов 3 и больше."
            ));
            if (favoriteScore != null) {
                habits.add(new ProfileHabitRow(
                        "Любимый счёт",
                        favoriteScore,
                        "Счёт, который ставите чаще всего (не меньше двух раз)."
                ));
            }
        }

        Map<String, Team> teamsByCode = new HashMap<>();
        for (Team t : DaoUtil.TEAMS.values()) {
            if (t != null && t.getCode() != null && !t.getCode().isBlank()) {
                teamsByCode.putIfAbsent(t.getCode(), t);
            }
        }
        List<ProfileTeamQualityRow> teams = teamsByCode.values().stream()
                .map(team -> {
                    TeamAgg a = byTeam.get(team.getCode());
                    int n = a == null ? 0 : a.n;
                    Double avg = (a == null || n == 0) ? null : round1(a.pointsSum / (double) n);
                    String hint = n == 0
                            ? "Пока нет завершённых прогнозов с участием " + team.getCode()
                            : "Средние очки в " + n + " матчах с участием " + team.getCode()
                            + ": " + formatNum(avg);
                    return new ProfileTeamQualityRow(
                            team.getCode(),
                            team.getName() != null ? team.getName() : team.getCode(),
                            teamLogoCacheService.pathForTeam(team.getPublicId()),
                            n,
                            avg,
                            hint
                    );
                })
                .sorted(Comparator
                        .comparing((ProfileTeamQualityRow r) -> r.avgPoints() == null)
                        .thenComparing(r -> r.avgPoints() == null ? 0.0 : r.avgPoints(), Comparator.reverseOrder())
                        .thenComparing(ProfileTeamQualityRow::teamCode))
                .toList();

        ProfileWeekHighlight bestWeek = weekHighlight(weekAgg, true);
        ProfileWeekHighlight worstWeek = weekHighlight(weekAgg, false);

        String bonusHint = bonusMatches > 0
                ? "В бонусных матчах в среднем " + formatNum(round1(bonusPointsSum / (double) bonusMatches)) + " очк."
                : "Бонус тура с 5-го тура: за точный счёт больше очков.";
        String cupHint = cupMatches > 0
                ? "В кубковых матчах в среднем " + formatNum(round1(cupPointsSum / (double) cupMatches)) + " очк."
                : "Кубковые прогнозы появятся после первого сыгранного матча.";

        return new ProfileStatsResponse(
                user.getLogin(),
                DaoUtil.currentWeekId > 0 ? "Тур " + currentWeekId : "",
                zhigalin.predictions.service.DataInitService.SEASON,
                currentWeekId,
                seasonPoints,
                currentWeekPoints,
                weekBonusMatchId,
                bonusLabel,
                finishedWithPick,
                finishedWithPick,
                highlights,
                breakdown,
                teams,
                habits,
                bestWeek,
                worstWeek,
                bonusMatches,
                bonusMatches > 0 ? round1(bonusPointsSum / (double) bonusMatches) : null,
                bonusHint,
                cupMatches,
                cupMatches > 0 ? round1(cupPointsSum / (double) cupMatches) : null,
                cupHint
        );
    }

    private List<UserPick> userPicks(int userId) {
        List<UserPick> picks = new ArrayList<>();
        for (MatchPrediction mp : predictionService.findAllByUserId(userId)) {
            if (mp.match() != null && mp.prediction() != null) {
                picks.add(new UserPick(Fixture.of(mp.match()), mp.prediction()));
            }
        }
        List<Prediction> cupPredictions = predictionService.findAllCupByUserId(userId);
        Map<Integer, CupMatch> cups = cupMatchDao.findAllByPublicIds(
                        cupPredictions.stream().map(Prediction::getCupMatchId).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(CupMatch::getPublicId, c -> c, (a, b) -> a));
        for (Prediction p : cupPredictions) {
            CupMatch c = cups.get(p.getCupMatchId());
            if (c != null) {
                picks.add(new UserPick(Fixture.of(c), p));
            }
        }
        return picks;
    }

    private boolean weekBonus(int userId, Fixture f) {
        return !f.cup() && userWeekBonusMatchService.isWeekBonusMatch(userId, f.weekId(), f.publicId());
    }

    private static int points(Fixture f, Prediction p, boolean weekBonus) {
        if (p.getPoints() != null) {
            return p.getPoints();
        }
        ScoringMode mode = f.cup() ? ScoringMode.CUP : weekBonus ? ScoringMode.EPL_WEEK_BONUS : ScoringMode.EPL;
        return PredictionService.computePoints(mode, f.homeScore(), f.awayScore(), p.getHomeTeamScore(), p.getAwayTeamScore());
    }

    private static String side(Integer teamId, String fallbackName) {
        String c = teamId == null ? null : code(teamId);
        if (c != null) {
            return c;
        }
        return fallbackName != null ? fallbackName : "—";
    }

    private static String formatPickTypeAvgs(
            int homeSum, int homeN, int drawSum, int drawN, int awaySum, int awayN
    ) {
        List<String> parts = new ArrayList<>(3);
        if (homeN > 0) {
            parts.add("П1 " + formatNum(round1(homeSum / (double) homeN)));
        }
        if (drawN > 0) {
            parts.add("X " + formatNum(round1(drawSum / (double) drawN)));
        }
        if (awayN > 0) {
            parts.add("П2 " + formatNum(round1(awaySum / (double) awayN)));
        }
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
    }

    private static ProfileBreakdownRow row(String label, int count, int total, String description) {
        return new ProfileBreakdownRow(label, count, formatPct(pct(count, total)), description);
    }

    private static ProfileWeekHighlight weekHighlight(Map<Integer, int[]> weekAgg, boolean best) {
        if (weekAgg.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, int[]> picked = null;
        for (Map.Entry<Integer, int[]> e : weekAgg.entrySet()) {
            if (e.getValue()[1] <= 0) {
                continue;
            }
            if (picked == null) {
                picked = e;
                continue;
            }
            int cmp = Integer.compare(e.getValue()[0], picked.getValue()[0]);
            if (best ? cmp > 0 : cmp < 0) {
                picked = e;
            } else if (cmp == 0 && e.getKey() > picked.getKey()) {
                picked = e;
            }
        }
        if (picked == null) {
            return null;
        }
        String desc = best
                ? "Тур с наибольшей суммой очков за сезон."
                : "Тур с наименьшей суммой очков за сезон.";
        return new ProfileWeekHighlight(picked.getKey(), picked.getValue()[0], desc);
    }

    private static void accumulateTeam(Map<String, TeamAgg> byTeam, Integer teamId, int pts) {
        String c = teamId == null ? null : code(teamId);
        if (c == null || c.isBlank()) {
            return;
        }
        TeamAgg a = byTeam.computeIfAbsent(c, k -> new TeamAgg());
        a.n++;
        a.pointsSum += pts;
    }

    private static String code(int teamId) {
        Team t = DaoUtil.team(teamId);
        return t != null ? t.getCode() : null;
    }

    private static boolean isFinished(String status) {
        return status != null && "ft".equalsIgnoreCase(status.trim());
    }

    static Bucket classify(Integer rh, Integer ra, Integer ph, Integer pa) {
        if (ph == null || pa == null) {
            return Bucket.NO_BET;
        }
        if (rh == null || ra == null) {
            return Bucket.NO_BET;
        }
        if (rh.equals(ph) && ra.equals(pa)) {
            return Bucket.EXACT;
        }
        if (rh - ra == ph - pa) {
            return Bucket.GOAL_DIFF;
        }
        boolean realHome = rh > ra;
        boolean realAway = rh < ra;
        boolean predHome = ph > pa;
        boolean predAway = ph < pa;
        if (realHome == predHome && realAway == predAway) {
            return Bucket.OUTCOME;
        }
        return Bucket.MISS;
    }

    private static String dominantPickLabel(int home, int draw, int away) {
        int max = Math.max(home, Math.max(draw, away));
        if (max == 0) {
            return "—";
        }
        List<String> parts = new ArrayList<>(3);
        if (home == max) {
            parts.add("хозяев");
        }
        if (draw == max) {
            parts.add("ничью");
        }
        if (away == max) {
            parts.add("гостей");
        }
        return String.join(" / ", parts);
    }

    private static double pct(int part, int total) {
        if (total <= 0) {
            return 0;
        }
        return 100.0 * part / total;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String formatPct(double pct) {
        if (pct == 0) {
            return "0%";
        }
        if (Math.abs(pct - Math.rint(pct)) < 0.05) {
            return ((int) Math.rint(pct)) + "%";
        }
        return String.format(Locale.US, "%.1f%%", pct);
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf((int) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }

    enum Bucket {
        EXACT, GOAL_DIFF, OUTCOME, MISS, NO_BET
    }

    private record Fixture(int publicId, boolean cup, Integer homeTeamId, Integer awayTeamId,
                           String homeName, String awayName, Integer homeScore, Integer awayScore,
                           String status, LocalDateTime kickoff, Integer weekId, String competition) {

        static Fixture of(Match m) {
            return new Fixture(m.getPublicId(), false, m.getHomeTeamId(), m.getAwayTeamId(), null, null,
                    m.getHomeTeamScore(), m.getAwayTeamScore(), m.getStatus(), m.getLocalDateTime(), m.getWeekId(), null);
        }

        static Fixture of(CupMatch c) {
            return new Fixture(c.getPublicId(), true, c.getHomeTeamId(), c.getAwayTeamId(),
                    c.getHomeName(), c.getAwayName(), c.getHomeTeamScore(), c.getAwayTeamScore(),
                    c.getStatus(), c.getLocalDateTime(), null, c.getCompetition());
        }
    }

    private record UserPick(Fixture fixture, Prediction prediction) {
    }

    private record TeamRow(Fixture fixture, Prediction prediction, Integer points, boolean weekBonus) {
    }

    private static final class TeamAgg {
        int n;
        int pointsSum;
    }
}
