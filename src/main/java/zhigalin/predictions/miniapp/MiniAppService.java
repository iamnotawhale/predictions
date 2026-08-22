package zhigalin.predictions.miniapp;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ChartSeries;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdMeterResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdScoreBucket;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.H2hItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardEntry;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LineupPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveMatchDetailsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchEventItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchInsightsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchNewsItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormItem;
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
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;
import static zhigalin.predictions.service.odds.OddsService.ODDS;

@Service
public class MiniAppService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter NEWS_TS = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final String SPORTS_RU_TEAM_RSS = "https://www.sports.ru/stat/export/rss/taglenta.xml?id=";
    private static final String ESPN_SUMMARY_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/summary";
    private static final Map<String, TeamKitColors> TEAM_PITCH_COLORS = loadTeamPitchColors();
    private static final long TEAM_NEWS_CACHE_MS = 120_000L;
    private static final Set<String> CLOSED_MATCH_STATUSES = Set.of(
            "ft", "aet", "pen", "canc", "abd", "awrd", "wo"
    );
    private static final Map<String, Integer> TEAM_NEWS_TAG_IDS = Map.ofEntries(
            Map.entry("ARS", 1685202),
            Map.entry("AST", 1315275),
            Map.entry("BOU", 5643539),
            Map.entry("BRE", 63451855),
            Map.entry("BRI", 5607747),
            Map.entry("CHE", 1046674),
            Map.entry("COV", 3612640),
            Map.entry("CRY", 3006569),
            Map.entry("EVE", 1300662),
            Map.entry("FUL", 1347893),
            Map.entry("HUL", 3096963),
            Map.entry("IPS", 3779605),
            Map.entry("LEE", 2793103),
            Map.entry("LIV", 1046732),
            Map.entry("MAC", 1328029),
            Map.entry("MUN", 1046599),
            Map.entry("NEW", 1062581),
            Map.entry("NOT", 3057313),
            Map.entry("SUN", 2682737),
            Map.entry("TOT", 2682611)
    );

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final HeadToHeadService headToHeadService;
    private final OddsService oddsService;
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedTeamNews> teamNewsCache = new ConcurrentHashMap<>();

    public MiniAppService(
            UserService userService,
            MatchService matchService,
            PredictionService predictionService,
            HeadToHeadService headToHeadService,
            OddsService oddsService,
            ApiClient apiClient,
            ObjectMapper objectMapper
    ) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.headToHeadService = headToHeadService;
        this.oddsService = oddsService;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
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

    public MatchInsightsResponse matchInsights(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match == null) {
            return new MatchInsightsResponse(List.of(), List.of(), List.of());
        }
        List<FormItem> homeForm = buildRecentForm(match.getHomeTeamId(), 5);
        List<FormItem> awayForm = buildRecentForm(match.getAwayTeamId(), 5);
        List<MatchNewsItem> news = loadMatchNews(match, 4);
        return new MatchInsightsResponse(homeForm, awayForm, news);
    }

    public LiveMatchDetailsResponse liveMatchDetails(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match == null) {
            return new LiveMatchDetailsResponse(
                    false, List.of(), List.of(), List.of(), List.of(), null, null, null, null, null);
        }
        boolean live = isLiveStatus(match.getStatus())
                       && match.getHomeTeamScore() != null
                       && match.getAwayTeamScore() != null;
        if (!live) {
            return new LiveMatchDetailsResponse(
                    false, List.of(), List.of(), List.of(), List.of(), null, null, null, null, null);
        }
        Map<Integer, List<Lineup>> lineups = apiClient.getLineups(match.getPublicId());
        List<LineupPlayerItem> homeLineup = toLineupItems(lineups.get(match.getHomeTeamId()));
        List<LineupPlayerItem> awayLineup = toLineupItems(lineups.get(match.getAwayTeamId()));
        JsonNode summaryRoot = loadEspnSummaryRoot(match);
        List<MatchEventItem> events = loadLiveEvents(summaryRoot);
        List<MatchStatItem> matchStats = loadLiveStats(summaryRoot);
        return new LiveMatchDetailsResponse(
                true,
                homeLineup,
                awayLineup,
                events,
                matchStats,
                pitchColorForTeamId(match.getHomeTeamId(), true),
                pitchColorForTeamId(match.getAwayTeamId(), false),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getStatus()
        );
    }

    public LeaderboardResponse leaderboard(String telegramId, Integer weekId) {
        requireUser(telegramId);
        List<LeaderboardEntry> entries;
        String title;
        boolean liveActive = false;
        if (weekId != null) {
            Map<String, Integer> points = predictionService.getWeeklyUsersPoints(weekId);
            if (weekId == DaoUtil.currentWeekId) {
                Map<String, Integer> provisional = computeCurrentWeekProvisionalPoints(weekId);
                List<LeaderboardEntry> liveEntries = new ArrayList<>();
                for (User user : DaoUtil.USERS.values()) {
                    String login = user.getLogin();
                    int base = points.getOrDefault(login, 0);
                    int prov = provisional.getOrDefault(login, 0);
                    int delta = prov - base;
                    if (delta != 0) {
                        liveActive = true;
                    }
                    liveEntries.add(new LeaderboardEntry(login, base, prov, delta));
                }
                title = liveActive ? "Очки за " + weekId + " тур (live)" : "Очки за " + weekId + " тур";
                entries = liveEntries.stream()
                        .sorted(Comparator.comparingInt((LeaderboardEntry e) -> e.provisionalPoints() != null ? e.provisionalPoints() : e.points())
                                .reversed()
                                .thenComparing(LeaderboardEntry::login))
                        .toList();
            } else {
                title = "Очки за " + weekId + " тур";
                entries = points.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry::getKey))
                        .map(e -> new LeaderboardEntry(e.getKey(), e.getValue(), null, null))
                        .toList();
            }
        } else {
            int currentWeekId = DaoUtil.currentWeekId;
            Map<String, Integer> seasonPoints = new LinkedHashMap<>(predictionService.getAllPointsByUsers());
            Map<String, Integer> weekStored = predictionService.getWeeklyUsersPoints(currentWeekId);
            Map<String, Integer> weekProvisional = computeCurrentWeekProvisionalPoints(currentWeekId);
            Map<String, Integer> seasonProvisional = new LinkedHashMap<>();

            for (User user : DaoUtil.USERS.values()) {
                String login = user.getLogin();
                int base = seasonPoints.getOrDefault(login, 0);
                int storedWeek = weekStored.getOrDefault(login, 0);
                int provisionalWeek = weekProvisional.getOrDefault(login, 0);
                int liveDelta = provisionalWeek - storedWeek;
                if (liveDelta != 0) {
                    liveActive = true;
                }
                seasonProvisional.put(login, base + liveDelta);
                seasonPoints.putIfAbsent(login, base);
            }
            title = liveActive ? "Общий зачёт (live)" : "Общий зачёт";
            entries = seasonProvisional.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(e -> {
                        String login = e.getKey();
                        int base = seasonPoints.getOrDefault(login, 0);
                        int provisional = e.getValue();
                        int delta = provisional - base;
                        return new LeaderboardEntry(login, base, provisional, delta);
                    })
                    .toList();
        }
        return new LeaderboardResponse(entries, weekId, title, liveActive);
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
                    .map(entry.getValue()::get)
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

    private Map<String, Integer> computeCurrentWeekProvisionalPoints(int weekId) {
        Map<String, Integer> weekProvisional = new LinkedHashMap<>();
        for (User user : DaoUtil.USERS.values()) {
            weekProvisional.put(user.getLogin(), 0);
        }
        List<Match> weekMatches = matchService.findAllByWeekId(weekId);
        for (Match match : weekMatches) {
            List<Prediction> preds = predictionService.getByMatchPublicId(match.getPublicId());
            Map<Integer, Prediction> byUser = preds.stream()
                    .collect(Collectors.toMap(Prediction::getUserId, p -> p, (left, right) -> left));
            boolean finished = isFinishedStatus(match.getStatus());
            boolean liveLike = isLiveStatus(match.getStatus())
                               || (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null
                                   && !finished && !"ns".equalsIgnoreCase(String.valueOf(match.getStatus())));
            if (!finished && !liveLike) {
                continue;
            }
            for (User user : DaoUtil.USERS.values()) {
                Prediction p = byUser.get(user.getId());
                int pts;
                if (finished && p != null && p.getPoints() != null) {
                    pts = p.getPoints();
                } else if (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null) {
                    pts = PredictionService.computePoints(
                            match.getHomeTeamScore(),
                            match.getAwayTeamScore(),
                            p != null ? p.getHomeTeamScore() : null,
                            p != null ? p.getAwayTeamScore() : null
                    );
                } else {
                    continue;
                }
                weekProvisional.merge(user.getLogin(), pts, Integer::sum);
            }
        }
        return weekProvisional;
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

    private List<LineupPlayerItem> toLineupItems(List<Lineup> lineup) {
        if (lineup == null || lineup.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> order = Map.of("G", 1, "D", 2, "M", 3, "F", 4);
        return lineup.stream()
                .filter(item -> item != null && item.getPlayer() != null)
                .sorted(Comparator.comparingInt(item -> order.getOrDefault(item.getPlayer().getPos(), 99)))
                .map(item -> new LineupPlayerItem(
                        item.getPlayer().getNumber(),
                        item.getPlayer().getName(),
                        item.getPlayer().getPos()
                ))
                .toList();
    }

    private JsonNode loadEspnSummaryRoot(Match match) {
        if (match == null || match.getEspnId() == null || match.getEspnId().isBlank()) {
            return null;
        }
        try {
            HttpResponse<String> response = Unirest.get(ESPN_SUMMARY_URL)
                    .queryString("event", match.getEspnId())
                    .asString();
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.warn("MiniApp summary fetch failed: matchId={}, espnId={}, error={}",
                    match.getPublicId(), match.getEspnId(), e.getMessage());
            return null;
        }
    }

    private List<MatchEventItem> loadLiveEvents(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        try {
            JsonNode commentary = root.path("commentary");
            if (!commentary.isArray() || commentary.isEmpty()) {
                return List.of();
            }
            List<LiveCommentaryEvent> parsed = new ArrayList<>();
            for (int i = 0; i < commentary.size(); i++) {
                JsonNode item = commentary.get(i);
                String text = item.path("text").asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                String minute = item.path("time").path("displayValue").asText("").trim();
                String type = item.path("play").path("type").path("type").asText("").trim();
                if (type.isBlank()) {
                    type = "comment";
                }
                if (shouldSkipCommentaryItem(text, type, minute)) {
                    continue;
                }
                double timeValue = item.path("time").path("value").asDouble(-1d);
                long sequence = item.path("sequence").asLong(i);
                int period = item.path("play").path("period").path("number").asInt(0);
                Double fieldX = item.path("play").path("fieldPositionX").isNumber()
                        ? item.path("play").path("fieldPositionX").asDouble()
                        : null;
                Double fieldY = item.path("play").path("fieldPositionY").isNumber()
                        ? item.path("play").path("fieldPositionY").asDouble()
                        : null;
                Double field2X = item.path("play").path("fieldPosition2X").isNumber()
                        ? item.path("play").path("fieldPosition2X").asDouble()
                        : null;
                Double field2Y = item.path("play").path("fieldPosition2Y").isNumber()
                        ? item.path("play").path("fieldPosition2Y").asDouble()
                        : null;
                Double goalPositionY = item.path("play").path("goalPositionY").isNumber()
                        ? item.path("play").path("goalPositionY").asDouble()
                        : null;
                String teamName = item.path("play").path("team").path("displayName").asText("").trim();
                String shortText = item.path("play").path("shortText").asText("").trim();
                String playerName = extractPrimaryParticipantName(item.path("play"));
                parsed.add(new LiveCommentaryEvent(
                        period, timeValue, sequence, minute, text, type,
                        fieldX, fieldY, field2X, field2Y, goalPositionY, teamName, shortText, playerName
                ));
            }
            if (parsed.isEmpty()) {
                return List.of();
            }
            parsed.sort(Comparator
                    .comparingInt(LiveCommentaryEvent::period)
                    .thenComparingDouble(LiveCommentaryEvent::timeValue)
                    .thenComparingLong(LiveCommentaryEvent::sequence)
                    .reversed());
            return parsed.stream()
                    .limit(22)
                    .map(item -> new MatchEventItem(
                            item.minute(),
                            item.text(),
                            item.type(),
                            item.period() > 0 ? item.period() : null,
                            item.fieldX(),
                            item.fieldY(),
                            item.field2X(),
                            item.field2Y(),
                            item.goalPositionY(),
                            item.teamName(),
                            item.shortText(),
                            item.playerName()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("MiniApp live commentary parse failed: error={}", e.getMessage());
            return List.of();
        }
    }

    private String extractPrimaryParticipantName(JsonNode play) {
        JsonNode participants = play.path("participants");
        if (!participants.isArray() || participants.isEmpty()) {
            return "";
        }
        String displayName = participants.get(0).path("athlete").path("displayName").asText("").trim();
        if (!displayName.isBlank()) {
            return displayName;
        }
        String lastName = participants.get(0).path("athlete").path("lastName").asText("").trim();
        return lastName;
    }

    private List<MatchStatItem> loadLiveStats(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        JsonNode teams = root.path("boxscore").path("teams");
        if (!teams.isArray() || teams.size() < 2) {
            return List.of();
        }
        Map<String, String> homeStats = extractStatsMap(teams.get(0).path("statistics"));
        Map<String, String> awayStats = extractStatsMap(teams.get(1).path("statistics"));
        if (homeStats.isEmpty() && awayStats.isEmpty()) {
            return List.of();
        }
        return List.of(
                statPercent("possessionPct", "possession", "Владение", homeStats, awayStats),
                stat("totalShots", "shotsTotal", "Удары", homeStats, awayStats),
                stat("shotsOnTarget", null, "В створ", homeStats, awayStats),
                stat("foulsCommitted", null, "Фолы", homeStats, awayStats),
                stat("offsides", null, "Офсайды", homeStats, awayStats),
                stat("wonCorners", "cornerKicks", "Угловые", homeStats, awayStats),
                stat("yellowCards", null, "ЖК", homeStats, awayStats),
                stat("redCards", null, "КК", homeStats, awayStats)
        );
    }

    private MatchStatItem stat(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                pickStatValue(home, primaryKey, fallbackKey),
                pickStatValue(away, primaryKey, fallbackKey)
        );
    }

    private MatchStatItem statPercent(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                formatPercent(pickStatValue(home, primaryKey, fallbackKey)),
                formatPercent(pickStatValue(away, primaryKey, fallbackKey))
        );
    }

    private String pickStatValue(Map<String, String> map, String primaryKey, String fallbackKey) {
        String value = map.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = fallbackKey != null ? map.get(fallbackKey) : null;
        }
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String formatPercent(String value) {
        if ("—".equals(value)) {
            return value;
        }
        if (value.endsWith("%")) {
            return value;
        }
        return value + "%";
    }

    private Map<String, String> extractStatsMap(JsonNode statsNode) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!statsNode.isArray()) {
            return map;
        }
        for (int i = 0; i < statsNode.size(); i++) {
            JsonNode item = statsNode.get(i);
            String name = item.path("name").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            String value = item.path("displayValue").asText("").trim();
            if (value.isBlank()) {
                value = item.path("value").asText("").trim();
            }
            if (!value.isBlank()) {
                map.put(name, value);
            }
        }
        return map;
    }

    private boolean shouldSkipCommentaryItem(String text, String type, String minute) {
        String normalizedText = text.toLowerCase();
        String normalizedType = type.toLowerCase();
        if (normalizedText.contains("lineups are announced")) {
            return true;
        }
        return minute.isBlank()
                && (normalizedType.contains("period") || normalizedType.contains("kickoff"));
    }

    private record LiveCommentaryEvent(
            int period,
            double timeValue,
            long sequence,
            String minute,
            String text,
            String type,
            Double fieldX,
            Double fieldY,
            Double field2X,
            Double field2Y,
            Double goalPositionY,
            String teamName,
            String shortText,
            String playerName
    ) {
    }

    private record TeamKitColors(int[] home, int[] away) {
    }

    private static Map<String, TeamKitColors> loadTeamPitchColors() {
        Map<String, TeamKitColors> map = new LinkedHashMap<>();
        try (InputStream input = MiniAppService.class.getClassLoader().getResourceAsStream("team_colors.json")) {
            if (input == null) {
                return map;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(input);
            root.fields().forEachRemaining(entry -> {
                JsonNode home = entry.getValue().path("home");
                JsonNode away = entry.getValue().path("away");
                map.put(entry.getKey(), new TeamKitColors(
                        new int[]{
                                home.path("r").asInt(255),
                                home.path("g").asInt(255),
                                home.path("b").asInt(255)
                        },
                        new int[]{
                                away.path("r").asInt(255),
                                away.path("g").asInt(255),
                                away.path("b").asInt(255)
                        }
                ));
            });
        } catch (Exception e) {
            LoggerFactory.getLogger(MiniAppService.class).warn("team_colors.json load failed: {}", e.getMessage());
        }
        return map;
    }

    private String pitchColorForTeamId(int teamId, boolean homeKit) {
        TeamKitColors kits = TEAM_PITCH_COLORS.get(String.valueOf(teamId));
        if (kits == null) {
            return "#ffffff";
        }
        int[] rgb = homeKit ? kits.home() : kits.away();
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    private List<FormItem> buildRecentForm(int teamId, int limit) {
        return matchService.findAll().stream()
                .filter(match -> match.getHomeTeamId() == teamId || match.getAwayTeamId() == teamId)
                .filter(match -> match.getHomeTeamScore() != null && match.getAwayTeamScore() != null)
                .filter(match -> isFinishedStatus(match.getStatus()))
                .sorted(Comparator.comparing(Match::getLocalDateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .map(match -> toFormItem(match, teamId))
                .toList();
    }

    private FormItem toFormItem(Match match, int teamId) {
        boolean teamIsHome = match.getHomeTeamId() == teamId;
        int ownScore = teamIsHome ? match.getHomeTeamScore() : match.getAwayTeamScore();
        int opponentScore = teamIsHome ? match.getAwayTeamScore() : match.getHomeTeamScore();
        int opponentId = teamIsHome ? match.getAwayTeamId() : match.getHomeTeamId();
        Team opponent = DaoUtil.TEAMS.get(opponentId);
        String outcome = ownScore > opponentScore ? "W" : ownScore < opponentScore ? "L" : "D";
        return new FormItem(
                outcome,
                ownScore,
                opponentScore,
                opponent != null ? opponent.getCode() : "?",
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : ""
        );
    }

    private List<MatchNewsItem> loadMatchNews(Match match, int limit) {
        if (match == null) {
            return List.of();
        }
        try {
            List<MatchNewsItem> news = new ArrayList<>();
            Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
            Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
            if (home != null) {
                news.addAll(loadTeamNews(home.getCode(), Math.max(limit, 3)));
            }
            if (away != null) {
                news.addAll(loadTeamNews(away.getCode(), Math.max(limit, 3)));
            }
            news = news.stream()
                    .filter(item -> item.title() != null && !item.title().isBlank())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    MatchNewsItem::url,
                                    item -> item,
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ),
                            map -> new ArrayList<>(map.values())
                    ));
            news.sort(Comparator.comparing(MatchNewsItem::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (news.size() > limit) {
                return news.subList(0, limit);
            }
            return news;
        } catch (Exception e) {
            log.warn("MiniApp match news load failed: matchId={}, error={}",
                    match.getPublicId(), e.getMessage());
            return List.of();
        }
    }

    private List<MatchNewsItem> loadTeamNews(String teamCode, int limit) {
        Integer tagId = TEAM_NEWS_TAG_IDS.get(teamCode);
        if (tagId == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CachedTeamNews cached = teamNewsCache.get(teamCode);
        if (cached != null && now - cached.cachedAtMs() <= TEAM_NEWS_CACHE_MS) {
            return cached.news();
        }
        try {
            URL source = new URL(SPORTS_RU_TEAM_RSS + tagId);
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(source));
            List<MatchNewsItem> items = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (items.size() >= limit) {
                    break;
                }
                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String url = entry.getLink() == null ? "" : entry.getLink().trim();
                if (title.isBlank() || url.isBlank()) {
                    continue;
                }
                String published = "";
                if (entry.getPublishedDate() != null) {
                    published = LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.of(AppTimeZones.DISPLAY.getId()))
                            .format(NEWS_TS);
                }
                items.add(new MatchNewsItem(title, url, published));
            }
            teamNewsCache.put(teamCode, new CachedTeamNews(now, items));
            return items;
        } catch (Exception e) {
            log.warn("MiniApp team news load failed: teamCode={}, tagId={}, error={}",
                    teamCode, tagId, e.getMessage());
            return List.of();
        }
    }

    private record CachedTeamNews(long cachedAtMs, List<MatchNewsItem> news) {
    }
}
