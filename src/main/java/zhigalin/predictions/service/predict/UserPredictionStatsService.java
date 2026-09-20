package zhigalin.predictions.service.predict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileBreakdownRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileHabitRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileHighlight;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileStatsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamQualityRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileWeekHighlight;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
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

    private static final int MIN_TEAM_SAMPLE = 2;
    private static final int TEAM_LIST_LIMIT = 5;

    private final PredictionService predictionService;
    private final MatchService matchService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final TeamLogoCacheService teamLogoCacheService;

    public UserPredictionStatsService(
            PredictionService predictionService,
            MatchService matchService,
            UserWeekBonusMatchService userWeekBonusMatchService,
            TeamLogoCacheService teamLogoCacheService
    ) {
        this.predictionService = predictionService;
        this.matchService = matchService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.teamLogoCacheService = teamLogoCacheService;
    }

    public ProfileStatsResponse build(User user) {
        int userId = user.getId();
        int currentWeekId = DaoUtil.currentWeekId;
        List<MatchPrediction> all = predictionService.findAllByUserId(userId);

        int seasonPoints = predictionService.getAllPointsByUsers()
                .getOrDefault(user.getLogin(), 0);
        int currentWeekPoints = predictionService.getWeeklyUsersPoints(currentWeekId)
                .getOrDefault(user.getLogin(), 0);

        String bonusLabel = null;
        Integer bonusMatchId = userWeekBonusMatchService.findAssigned(userId, currentWeekId).orElse(null);
        if (bonusMatchId != null) {
            Match bonusMatch = matchService.findByPublicId(bonusMatchId);
            if (bonusMatch != null) {
                String home = code(bonusMatch.getHomeTeamId());
                String away = code(bonusMatch.getAwayTeamId());
                if (home != null && away != null) {
                    bonusLabel = home + "–" + away;
                }
            }
            if (bonusLabel == null) {
                bonusLabel = all.stream()
                        .filter(mp -> mp.match().getPublicId() == bonusMatchId)
                        .findFirst()
                        .map(mp -> code(mp.match().getHomeTeamId()) + "–" + code(mp.match().getAwayTeamId()))
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
        int pickScored = 0;

        int bonusMatches = 0;
        int bonusPointsSum = 0;

        for (MatchPrediction mp : all) {
            Match match = mp.match();
            Prediction pred = mp.prediction();
            if (match == null || pred == null) {
                continue;
            }
            if (!isFinished(match.getStatus())) {
                continue;
            }
            Integer rh = match.getHomeTeamScore();
            Integer ra = match.getAwayTeamScore();
            Integer ph = pred.getHomeTeamScore();
            Integer pa = pred.getAwayTeamScore();

            boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                    userId, match.getWeekId(), match.getPublicId());
            ScoringMode mode = weekBonus ? ScoringMode.EPL_WEEK_BONUS : ScoringMode.EPL;
            int pts = pred.getPoints() != null
                    ? pred.getPoints()
                    : PredictionService.computePoints(mode, rh, ra, ph, pa);

            Bucket bucket = classify(rh, ra, ph, pa);
            if (bucket == Bucket.NO_BET) {
                continue;
            }
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
            if (ph > pa) {
                pickHome++;
            } else if (Objects.equals(ph, pa)) {
                pickDraw++;
            } else {
                pickAway++;
            }
            if (ph > 0 && pa > 0) {
                pickBtts++;
            }
            if (ph + pa >= 3) {
                pickOver25++;
            }
            accumulateTeam(byTeam, match.getHomeTeamId(), pts);
            accumulateTeam(byTeam, match.getAwayTeamId(), pts);

            weekAgg.computeIfAbsent(match.getWeekId(), w -> new int[2]);
            int[] wa = weekAgg.get(match.getWeekId());
            wa[0] += pts;
            wa[1] += 1;

            if (weekBonus) {
                bonusMatches++;
                bonusPointsSum += pts;
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
                        "Средние очки за матч с прогнозом (Англия, с учётом бонуса тура)."
                ),
                new ProfileHighlight(
                        "С прогнозом",
                        String.valueOf(finishedWithPick),
                        "Сколько завершённых матчей АПЛ вы уже спрогнозировали."
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
                    "Среди ваших прогнозов с цифрами: победа хозяев / ничья / гости."
            ));
            habits.add(new ProfileHabitRow(
                    "Обе забьют",
                    formatPct(pct(pickBtts, pickScored)),
                    "Доля прогнозов, где обеим командам ставите хотя бы по голу."
            ));
            habits.add(new ProfileHabitRow(
                    "Тотал 2.5+",
                    formatPct(pct(pickOver25, pickScored)),
                    "Доля прогнозов с суммой голов 3 и больше."
            ));
            habits.add(new ProfileHabitRow(
                    "Ср. тотал в прогнозе",
                    formatNum(round1(pickTotalGoalsSum / (double) pickScored)),
                    "Средняя сумма голов в вашем счёте."
            ));
            if (favoriteScore != null) {
                habits.add(new ProfileHabitRow(
                        "Любимый счёт",
                        favoriteScore,
                        "Счёт, который вы ставите чаще всего (не меньше двух раз)."
                ));
            }
        }

        List<ProfileTeamQualityRow> ranked = byTeam.entrySet().stream()
                .filter(e -> e.getValue().n >= MIN_TEAM_SAMPLE)
                .map(e -> {
                    TeamAgg a = e.getValue();
                    Team team = DaoUtil.TEAMS.values().stream()
                            .filter(t -> e.getKey().equals(t.getCode()))
                            .findFirst()
                            .orElse(null);
                    double avg = round1(a.pointsSum / (double) a.n);
                    return new ProfileTeamQualityRow(
                            e.getKey(),
                            team != null ? team.getName() : e.getKey(),
                            team != null ? teamLogoCacheService.pathForTeam(team.getPublicId()) : null,
                            a.n,
                            avg,
                            "Средние очки в " + a.n + " матчах с участием " + e.getKey()
                                    + ": " + formatNum(avg)
                    );
                })
                .toList();

        List<ProfileTeamQualityRow> bestTeams = ranked.stream()
                .filter(r -> r.avgPoints() > 0)
                .sorted(Comparator.comparingDouble(ProfileTeamQualityRow::avgPoints).reversed()
                        .thenComparingInt(ProfileTeamQualityRow::matches).reversed())
                .limit(TEAM_LIST_LIMIT)
                .toList();
        java.util.Set<String> bestCodes = bestTeams.stream()
                .map(ProfileTeamQualityRow::teamCode)
                .collect(java.util.stream.Collectors.toSet());
        List<ProfileTeamQualityRow> worstTeams = ranked.stream()
                .filter(r -> !bestCodes.contains(r.teamCode()))
                .sorted(Comparator.comparingDouble(ProfileTeamQualityRow::avgPoints)
                        .thenComparingInt(ProfileTeamQualityRow::matches).reversed())
                .limit(TEAM_LIST_LIMIT)
                .toList();

        ProfileWeekHighlight bestWeek = weekHighlight(weekAgg, true);
        ProfileWeekHighlight worstWeek = weekHighlight(weekAgg, false);

        String bonusHint = bonusMatches > 0
                ? "В бонусных матчах в среднем " + formatNum(round1(bonusPointsSum / (double) bonusMatches)) + " очк."
                : "Бонус тура с 5-го тура: за точный счёт больше очков.";

        return new ProfileStatsResponse(
                user.getLogin(),
                DaoUtil.currentWeekId > 0 ? "Тур " + currentWeekId : "",
                zhigalin.predictions.service.DataInitService.SEASON,
                currentWeekId,
                seasonPoints,
                currentWeekPoints,
                bonusMatchId,
                bonusLabel,
                finishedWithPick,
                finishedWithPick,
                highlights,
                breakdown,
                bestTeams,
                worstTeams,
                habits,
                bestWeek,
                worstWeek,
                bonusMatches,
                bonusMatches > 0 ? round1(bonusPointsSum / (double) bonusMatches) : null,
                bonusHint
        );
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

    private static void accumulateTeam(Map<String, TeamAgg> byTeam, int teamId, int pts) {
        String c = code(teamId);
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

    private static final class TeamAgg {
        int n;
        int pointsSum;
    }
}
