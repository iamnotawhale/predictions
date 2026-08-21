package zhigalin.predictions.miniapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ChartSeries;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdMeterResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdScoreBucket;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.H2hItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardEntry;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveRaceEntry;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveRaceResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewResponse;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.DaoUtil;
import static zhigalin.predictions.service.odds.OddsService.ODDS;

@Service
public class MiniAppService {

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final Set<String> CLOSED_MATCH_STATUSES = Set.of(
            "ft", "aet", "pen", "canc", "abd", "awrd", "wo"
    );

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final HeadToHeadService headToHeadService;
    private final OddsService oddsService;

    public MiniAppService(
            UserService userService,
            MatchService matchService,
            PredictionService predictionService,
            HeadToHeadService headToHeadService,
            OddsService oddsService
    ) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.headToHeadService = headToHeadService;
        this.oddsService = oddsService;
    }

    public User requireUser(String telegramId) {
        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            throw new MiniAppException(403, "Пользователь не найден. Обратитесь к администратору бота.");
        }
        return user;
    }

    public ProfileResponse profile(String telegramId) {
        User user = requireUser(telegramId);
        int weekId = DaoUtil.currentWeekId;
        return new ProfileResponse(
                user.getLogin(),
                weekId,
                DataInitService.SEASON,
                "Тур " + weekId + " · сезон " + DataInitService.SEASON
        );
    }

    public List<WeekItem> weeks(String telegramId) {
        requireUser(telegramId);
        Set<Integer> withPredictions = new HashSet<>(predictionService.getPredictableWeeksByUserTelegramId(telegramId));
        List<WeekItem> items = new ArrayList<>();
        for (int week = 1; week <= 38; week++) {
            items.add(new WeekItem(week, withPredictions.contains(week)));
        }
        return items;
    }

    public List<MatchItem> weekMatches(String telegramId, int weekId) {
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        oddsService.ensureFresh(matches);
        Set<Integer> withPrediction = new HashSet<>(
                matchService.predictableMatchesByUserTelegramIdAndWeekId(telegramId, weekId)
        );
        return matches.stream()
                .map(match -> toMatchItem(match, telegramId, withPrediction.contains(match.getPublicId())))
                .toList();
    }

    public List<MatchItem> myPredictions(String telegramId, int weekId) {
        requireUser(telegramId);
        return predictionService.getAllWeeklyPredictionsByUserTelegramId(weekId, telegramId).stream()
                .map(mp -> toMatchItem(mp.match(), telegramId, true, mp.prediction()))
                .toList();
    }

    public MatchItem match(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match != null) {
            oddsService.ensureFresh(List.of(match));
        }
        Prediction prediction = predictionService.getByUserTelegramIdAndTeams(
                telegramId, homeCode.toUpperCase(), awayCode.toUpperCase()
        );
        boolean hasPrediction = prediction != null;
        return toMatchItem(match, telegramId, hasPrediction, prediction);
    }

    public LeaderboardResponse leaderboard(String telegramId, Integer weekId) {
        requireUser(telegramId);
        Map<String, Integer> points;
        String title;
        if (weekId != null) {
            points = predictionService.getWeeklyUsersPoints(weekId);
            title = "Очки за " + weekId + " тур";
        } else {
            points = predictionService.getAllPointsByUsers();
            title = "Общий зачёт";
        }
        List<LeaderboardEntry> entries = points.entrySet().stream()
                .map(e -> new LeaderboardEntry(e.getKey(), e.getValue()))
                .toList();
        return new LeaderboardResponse(entries, weekId, title);
    }

    public TodayMatchesResponse todayMatches(String telegramId) {
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByTodayDate();
        oddsService.ensureFresh(matches);
        Set<Integer> withPrediction = new HashSet<>(
                matchService.predictableTodayMatchesByUserTelegramIdAndWeekId(telegramId)
        );
        List<MatchItem> items = matches.stream()
                .map(match -> toMatchItem(match, telegramId, withPrediction.contains(match.getPublicId())))
                .sorted(todayMatchOrder())
                .toList();
        boolean hasLive = items.stream().anyMatch(m -> isLiveStatus(m.status()));
        return new TodayMatchesResponse(items, hasLive);
    }

    public CrowdMeterResponse crowdMeter(String telegramId, int matchPublicId) {
        requireUser(telegramId);
        Match match = matchService.findByPublicId(matchPublicId);
        if (match == null) {
            throw new MiniAppException(404, "Матч не найден");
        }
        List<Prediction> predictions = predictionService.getByMatchPublicId(matchPublicId).stream()
                .filter(p -> p.getHomeTeamScore() != null && p.getAwayTeamScore() != null)
                .toList();
        int total = predictions.size();
        if (total == 0) {
            return new CrowdMeterResponse(matchPublicId, 0, 0, 0, 0, List.of());
        }
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        Map<String, Integer> scoreCounts = new LinkedHashMap<>();
        for (Prediction p : predictions) {
            int hs = p.getHomeTeamScore();
            int as = p.getAwayTeamScore();
            if (hs > as) {
                homeWins++;
            } else if (hs < as) {
                awayWins++;
            } else {
                draws++;
            }
            String key = hs + ":" + as;
            scoreCounts.merge(key, 1, Integer::sum);
        }
        List<CrowdScoreBucket> top = scoreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> new CrowdScoreBucket(e.getKey(), e.getValue(), percent(e.getValue(), total)))
                .toList();
        return new CrowdMeterResponse(
                matchPublicId,
                total,
                percent(homeWins, total),
                percent(draws, total),
                percent(awayWins, total),
                top
        );
    }

    public LiveRaceResponse liveRace(String telegramId) {
        requireUser(telegramId);
        int weekId = DaoUtil.currentWeekId;
        List<Match> weekMatches = matchService.findAllByWeekId(weekId);
        boolean hasLiveOrStarted = weekMatches.stream().anyMatch(m ->
                isLiveStatus(m.getStatus()) || isFinishedStatus(m.getStatus())
                || (m.getHomeTeamScore() != null && m.getAwayTeamScore() != null
                    && !"ns".equalsIgnoreCase(String.valueOf(m.getStatus()))));
        if (!hasLiveOrStarted) {
            return new LiveRaceResponse(weekId, false, List.of());
        }
        Map<String, Integer> points = new LinkedHashMap<>();
        Map<String, Integer> provisional = new LinkedHashMap<>();
        for (User user : DaoUtil.USERS.values()) {
            points.put(user.getLogin(), 0);
            provisional.put(user.getLogin(), 0);
        }
        for (Match match : weekMatches) {
            List<Prediction> preds = predictionService.getByMatchPublicId(match.getPublicId());
            boolean finished = isFinishedStatus(match.getStatus());
            boolean liveLike = isLiveStatus(match.getStatus())
                               || (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null
                                   && !finished && !"ns".equalsIgnoreCase(String.valueOf(match.getStatus())));
            for (Prediction p : preds) {
                User user = DaoUtil.USERS.get(p.getUserId());
                if (user == null) {
                    continue;
                }
                if (finished && p.getPoints() != null) {
                    points.merge(user.getLogin(), Math.max(p.getPoints(), 0), Integer::sum);
                    provisional.merge(user.getLogin(), Math.max(p.getPoints(), 0), Integer::sum);
                } else if (liveLike) {
                    int livePts = PredictionService.computePoints(
                            match.getHomeTeamScore(),
                            match.getAwayTeamScore(),
                            p.getHomeTeamScore(),
                            p.getAwayTeamScore()
                    );
                    provisional.merge(user.getLogin(), Math.max(livePts, 0), Integer::sum);
                }
            }
        }
        List<LiveRaceEntry> entries = provisional.entrySet().stream()
                .map(e -> new LiveRaceEntry(e.getKey(), points.getOrDefault(e.getKey(), 0), e.getValue()))
                .sorted(Comparator.comparingInt(LiveRaceEntry::provisionalPoints).reversed()
                        .thenComparing(LiveRaceEntry::login))
                .toList();
        return new LiveRaceResponse(weekId, true, entries);
    }

    public WeekReviewResponse weekReview(String telegramId, int weekId) {
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        List<WeekReviewItem> items = new ArrayList<>();
        int total = 0;
        for (Match match : matches) {
            Prediction prediction = predictionService.getByUserTelegramIdAndTeams(
                    telegramId,
                    teamCode(match.getHomeTeamId()),
                    teamCode(match.getAwayTeamId())
            );
            boolean has = prediction != null;
            Integer pts = has ? prediction.getPoints() : null;
            if (pts != null && pts > 0) {
                total += pts;
            }
            items.add(new WeekReviewItem(
                    match.getPublicId(),
                    teamCode(match.getHomeTeamId()),
                    teamCode(match.getAwayTeamId()),
                    match.getStatus(),
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    has ? prediction.getHomeTeamScore() : null,
                    has ? prediction.getAwayTeamScore() : null,
                    pts,
                    has
            ));
        }
        return new WeekReviewResponse(weekId, total, items);
    }

    public PointsChartResponse pointsChart(String telegramId) {
        requireUser(telegramId);
        Map<String, Map<Integer, Integer>> raw = predictionService.getAllUsersCumulativePoints().entrySet().stream()
                .sorted((e1, e2) -> {
                    int max1 = e1.getValue().values().stream().max(Integer::compareTo).orElse(0);
                    int max2 = e2.getValue().values().stream().max(Integer::compareTo).orElse(0);
                    return Integer.compare(max2, max1);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        Set<Integer> weekSet = new TreeSet<>();
        raw.values().forEach(m -> weekSet.addAll(m.keySet()));
        List<Integer> weeks = new ArrayList<>(weekSet);

        List<ChartSeries> series = new ArrayList<>();
        int limit = Math.min(6, raw.size());
        int i = 0;
        for (Map.Entry<String, Map<Integer, Integer>> entry : raw.entrySet()) {
            if (i++ >= limit) {
                break;
            }
            String login = entry.getKey();
            String label = login.length() >= 3 ? login.substring(0, 3).toUpperCase() : login.toUpperCase();
            List<Integer> points = weeks.stream()
                    .map(w -> entry.getValue().getOrDefault(w, null))
                    .map(v -> v == null ? -1 : v)
                    .toList();
            series.add(new ChartSeries(login, label, points));
        }
        return new PointsChartResponse(weeks, series);
    }

    public List<StandingItem> standings(String telegramId) {
        requireUser(telegramId);
        List<Standing> standings = matchService.getStandings();
        List<StandingItem> items = new ArrayList<>();
        int place = 1;
        for (Standing standing : standings) {
            Team team = DaoUtil.TEAMS.get(standing.getTeamId());
            items.add(new StandingItem(
                    place++,
                    team.getCode(),
                    team.getName(),
                    teamLogoPath(standing.getTeamId()),
                    standing.getGames(),
                    standing.getWon(),
                    standing.getDrawn(),
                    standing.getLost(),
                    standing.getGoalsFor(),
                    standing.getGoalsAgainst(),
                    standing.getPoints()
            ));
        }
        return items;
    }

    public TeamMatchesResponse teamMatches(String telegramId, String teamCode) {
        requireUser(telegramId);
        String normalizedCode = teamCode.toUpperCase();
        Team team = DaoUtil.TEAMS.values().stream()
                .filter(t -> normalizedCode.equals(t.getCode()))
                .findFirst()
                .orElseThrow(() -> new MiniAppException(404, "Команда не найдена"));

        LocalDateTime now = LocalDateTime.now();
        List<Match> allMatches = matchService.findAll().stream()
                .filter(match -> match.getHomeTeamId() == team.getPublicId() || match.getAwayTeamId() == team.getPublicId())
                .sorted(Comparator.comparing(Match::getLocalDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<TeamMatchItem> lastMatches = allMatches.stream()
                .filter(match -> match.getLocalDateTime() != null)
                .filter(match -> match.getLocalDateTime().isBefore(now) || match.getResult() != null)
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .limit(5)
                .map(this::toTeamMatchItem)
                .toList();

        List<TeamMatchItem> upcomingMatches = allMatches.stream()
                .filter(match -> match.getLocalDateTime() != null && match.getLocalDateTime().isAfter(now))
                .limit(5)
                .map(this::toTeamMatchItem)
                .toList();

        return new TeamMatchesResponse(team.getCode(), team.getName(), lastMatches, upcomingMatches);
    }

    public List<H2hItem> h2h(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        List<HeadToHead> items = headToHeadService.findAllByTwoTeamsCode(homeCode.toUpperCase(), awayCode.toUpperCase());
        return items.stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(10)
                .map(h -> new H2hItem(
                        h.getLeagueName(),
                        h.getLocalDateTime() != null ? h.getLocalDateTime().format(KICKOFF) : "",
                        teamCode(h.getHomeTeamId()),
                        teamCode(h.getAwayTeamId()),
                        h.getHomeTeamScore(),
                        h.getAwayTeamScore()
                ))
                .toList();
    }

    public ActionResponse savePrediction(String telegramId, PredictRequest request) {
        requireUser(telegramId);
        String home = request.homeCode().toUpperCase();
        String away = request.awayCode().toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!canPredict(match)) {
            return new ActionResponse(false, "Время для прогноза истекло. Матч уже начался.");
        }
        if (request.homeScore() < 0 || request.homeScore() > 5 || request.awayScore() < 0 || request.awayScore() > 5) {
            return new ActionResponse(false, "Счёт должен быть от 0 до 5.");
        }
        boolean exists = predictionService.isExist(telegramId, match.getPublicId());
        predictionService.save(telegramId, home, away, request.homeScore(), request.awayScore());
        return new ActionResponse(
                true,
                exists ? "Прогноз обновлён" : "Прогноз сохранён",
                request.homeScore(),
                request.awayScore()
        );
    }

    public ActionResponse deletePrediction(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        String home = homeCode.toUpperCase();
        String away = awayCode.toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!canPredict(match)) {
            return new ActionResponse(false, "Время для удаления прогноза истекло.");
        }
        predictionService.deleteByUserTelegramIdAndTeams(telegramId, home, away);
        return new ActionResponse(true, "Прогноз удалён");
    }

    private MatchItem toMatchItem(Match match, String telegramId, boolean hasPrediction) {
        Prediction prediction = hasPrediction
                ? predictionService.getByUserTelegramIdAndTeams(
                        telegramId,
                        teamCode(match.getHomeTeamId()),
                        teamCode(match.getAwayTeamId())
                )
                : null;
        return toMatchItem(match, telegramId, hasPrediction, prediction);
    }

    private MatchItem toMatchItem(Match match, String telegramId, boolean hasPrediction, Prediction prediction) {
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        OddsService.Odd odd = ODDS.get(match.getPublicId());
        LocalDateTime until = match.getLocalDateTime() == null ? null : match.getLocalDateTime().plusMinutes(5);
        Long secondsLeft = null;
        if (until != null && canPredict(match)) {
            secondsLeft = java.time.Duration.between(LocalDateTime.now(), until).getSeconds();
            if (secondsLeft < 0) {
                secondsLeft = 0L;
            }
        }
        return new MatchItem(
                match.getPublicId(),
                match.getWeekId(),
                home.getCode(),
                home.getName(),
                teamLogoPath(match.getHomeTeamId()),
                away.getCode(),
                away.getName(),
                teamLogoPath(match.getAwayTeamId()),
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : "",
                canPredict(match),
                hasPrediction,
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null,
                prediction != null ? prediction.getPoints() : null,
                odd != null ? odd.home() : null,
                odd != null ? odd.draw() : null,
                odd != null ? odd.away() : null,
                until != null ? until.format(KICKOFF) : null,
                secondsLeft
        );
    }

    private static Comparator<MatchItem> todayMatchOrder() {
        return Comparator
                .comparing((MatchItem m) -> !isLiveStatus(m.status()))
                .thenComparing(MatchItem::hasPrediction)
                .thenComparing(MatchItem::kickoff, Comparator.nullsLast(String::compareTo));
    }

    private static boolean isLiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.toLowerCase();
        return !Set.of("ns", "ft", "aet", "pen", "pst", "canc", "abd", "awrd", "wo").contains(s);
    }

    private static boolean isFinishedStatus(String status) {
        return status != null && CLOSED_MATCH_STATUSES.contains(status.toLowerCase());
    }

    private static int percent(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    private static boolean canPredict(Match match) {
        if (match == null || match.getLocalDateTime() == null) {
            return false;
        }
        String status = match.getStatus();
        if (status != null && CLOSED_MATCH_STATUSES.contains(status.toLowerCase())) {
            return false;
        }
        return LocalDateTime.now().isBefore(match.getLocalDateTime().plusMinutes(5));
    }

    private static String teamCode(int teamId) {
        return DaoUtil.TEAMS.get(teamId).getCode();
    }

    private static String teamLogoPath(int teamId) {
        return "/img/teams/" + teamId + ".webp";
    }

    private TeamMatchItem toTeamMatchItem(Match match) {
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        return new TeamMatchItem(
                match.getPublicId(),
                match.getWeekId(),
                home.getCode(),
                home.getName(),
                teamLogoPath(match.getHomeTeamId()),
                away.getCode(),
                away.getName(),
                teamLogoPath(match.getAwayTeamId()),
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : ""
        );
    }
}
