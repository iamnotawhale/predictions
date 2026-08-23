package zhigalin.predictions.service;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.input.Fixture;
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.ResponseTeam;
import zhigalin.predictions.model.input.Root;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Event;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;

@Service
public class DataInitService {
    @Value("${api.football.token}")
    private String apiFootballToken;

    private final TeamService teamService;
    private final WeekService weekService;
    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;
    private final ApiClient apiClient;

    public static final int SEASON = 2026;
    private static final String X_RAPIDAPI_KEY = "x-rapidapi-key";
    private static final String HOST_NAME = "x-rapidapi-host";
    private static final String HOST = "v3.football.api-sports.io";
    private static final String FIXTURES_URL = "https://v3.football.api-sports.io/fixtures";
    private static final long LIVE_INTERVAL_MS = 30_000L;
    private static final long IDLE_INTERVAL_MS = 120_000L;
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    private volatile long lastDataInitAtMs = 0L;
    private volatile long dataInitIntervalMs = LIVE_INTERVAL_MS;

    public DataInitService(TeamService teamService, WeekService weekService, MatchService matchService,
                           HeadToHeadService headToHeadService, NotificationService notificationService,
                           PanicSender panicSender, ApiClient apiClient
    ) {
        this.teamService = teamService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.notificationService = notificationService;
        this.panicSender = panicSender;
        this.apiClient = apiClient;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    private void start() {
        long now = System.currentTimeMillis();
        if (now - lastDataInitAtMs < dataInitIntervalMs) {
            return;
        }
        lastDataInitAtMs = now;
        serverLogger.info("Data init start (interval={}s)", dataInitIntervalMs / 1000);
        try {
            matchUpdateFromESPN();
        } catch (Exception e) {
            serverLogger.error("matchUpdateFromESPN error: {}", e.getMessage(), e);
            panicSender.sendPanic("matchUpdateFromESPN", e);
        }
        try {
            notificationService.checkReminders();
        } catch (Exception e) {
            serverLogger.error("checkReminders error: {}", e.getMessage(), e);
            panicSender.sendPanic("checkReminders", e);
        }
        boolean busy = !matchService.findOnlineMatches().isEmpty()
                       || !matchService.findAllNearest(90).isEmpty();
        dataInitIntervalMs = busy ? LIVE_INTERVAL_MS : IDLE_INTERVAL_MS;
    }

    @Scheduled(cron = "0 50 8 * * *")
    private void matchUpdate() throws JsonProcessingException {
        matchDateTimeStatusUpdate();
    }

    public void syncMatchTimesFromApi() throws JsonProcessingException {
        matchDateTimeStatusUpdate();
    }

    private void matchUpdateFromESPN() throws JsonProcessingException {
        List<Match> currentWeekMatches = matchService.findAllByCurrentWeek();
        if (!currentWeekMatches.isEmpty() && currentWeekMatches.stream()
                .allMatch(m -> Objects.equals(m.getStatus(), "ft")
                               || Objects.equals(m.getStatus(), "pst"))) {
            notificationService.sendWeeklyResults();
            weekService.updateCurrent();
        }

        boolean hasOnlineMatches = !matchService.findOnlineMatches().isEmpty();
        boolean hasPostponedMatches = matchService.findAll().stream()
                .anyMatch(m -> "pst".equals(m.getStatus()));

        if (!hasOnlineMatches && !hasPostponedMatches) return;

        HttpResponse<String> response = Unirest.get("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard")
                .asString();

        Scoreboard scoreboard = mapper.readValue(response.getBody(), Scoreboard.class);
        List<Event> events = scoreboard.getEvents();

        for (Event event : events) {
            String state = event.getStatus().getType().getState();
            String[] teams = event.getShortName().split(" @ ");
            String homeTeam = realTeamCode(teams[1]);
            String awayTeam = realTeamCode(teams[0]);

            if (state.equals("pre")) {
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }
                boolean updated = false;
                if (event.getId() != null && !event.getId().isBlank() && !event.getId().equals(match.getEspnId())) {
                    match.setEspnId(event.getId());
                    updated = true;
                }
                if ("pst".equals(match.getStatus())) {
                    String dateStr = event.getDate().replaceAll("(T\\d{2}:\\d{2})Z", "$1:00Z");
                    LocalDateTime espnDate = Instant.parse(dateStr)
                            .atZone(AppTimeZones.DISPLAY)
                            .toLocalDateTime();
                    match.setStatus("ns");
                    match.setLocalDateTime(espnDate);
                    updated = true;
                    serverLogger.info("Match {}-{} rescheduled: pst -> ns, date={}", homeTeam, awayTeam, espnDate);
                }
                if (updated) {
                    matchService.update(match);
                }
            } else if (state.equals("in")) {
                String status = normalizeEspnLiveStatus(event);
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }

                Integer homeScore = findScore(event, "home");
                Integer awayScore = findScore(event, "away");
                Integer prevHome = match.getHomeTeamScore();
                Integer prevAway = match.getAwayTeamScore();
                int prevTotal = (prevHome == null ? 0 : prevHome) + (prevAway == null ? 0 : prevAway);
                int nextTotal = homeScore + awayScore;

                match.setHomeTeamScore(homeScore);
                match.setAwayTeamScore(awayScore);
                match.setStatus(status);
                match.setEspnId(event.getId());
                match.setResult(findResult(homeScore, awayScore));

                matchService.update(match);
                if (nextTotal > prevTotal) {
                    notificationService.sendLiveScoreUpdate(match, prevHome, prevAway);
                }
            } else if (state.equals("post")) {
                String status = "ft";
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }
                apiClient.evictLineups(match.getPublicId());

                if (!status.equals(match.getStatus())) {
                    Integer homeScore = findScore(event, "home");
                    Integer awayScore = findScore(event, "away");

                    match.setHomeTeamScore(homeScore);
                    match.setAwayTeamScore(awayScore);
                    match.setStatus(status);
                    match.setResult(findResult(homeScore, awayScore));

                    matchService.update(match);
                }
            }
        }
    }

    private String normalizeEspnLiveStatus(Event event) {
        if (event == null || event.getStatus() == null) {
            return null;
        }
        zhigalin.predictions.model.v2.Type type = event.getStatus().getType();
        String displayClock = event.getStatus().getDisplayClock();
        if (type == null) {
            return displayClock;
        }
        if (Boolean.TRUE.equals(type.getCompleted()) || "post".equalsIgnoreCase(type.getState())) {
            return "ft";
        }
        String detail = type.getDetail();
        String shortDetail = type.getShortDetail();
        String description = type.getDescription();
        if (containsHalftime(detail) || containsHalftime(shortDetail) || containsHalftime(description)) {
            return "ht";
        }
        return displayClock;
    }

    private boolean containsHalftime(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("halftime")
                || normalized.contains("half-time")
                || normalized.equals("ht")
                || normalized.startsWith("ht ")
                || normalized.endsWith(" ht");
    }

    private String realTeamCode(String teamCode) {
        return switch (teamCode) {
            case "AVL" -> "AST";
            case "BHA" -> "BRI";
            case "WHU" -> "WES";
            case "MNC" -> "MCI";
            case "NFO" -> "NOT";
            case "MAN" -> "MUN";
            default -> teamCode;
        };
    }

    private Integer findScore(Event event, String awayHome) {
        List<Competitor> competitors = event.getCompetitions().getFirst().getCompetitors();
        Competitor competitor = competitors.stream()
                .filter(c -> c.getHomeAway().equals(awayHome))
                .findFirst()
                .orElseThrow();

        return Integer.parseInt(competitor.getScore());
    }

    private String findResult(Integer homeScore, Integer awayScore) {
        String result;
        if (homeScore.equals(awayScore)) {
            result = "D";
        } else {
            result = homeScore > awayScore ? "H" : "A";
        }
        return result;
    }

    private void matchUpdateFromApiFootball() throws JsonProcessingException {
        List<Match> currentWeekMatches = matchService.findAllByCurrentWeek();
        if (!currentWeekMatches.isEmpty() && currentWeekMatches.stream()
                .allMatch(m -> Objects.equals(m.getStatus(), "ft")
                               || Objects.equals(m.getStatus(), "pst"))) {
            notificationService.sendWeeklyResults();
            weekService.updateCurrent();
        }
        if (!matchService.findOnlineMatches().isEmpty()) {
            serverLogger.info("Matches to update found");
            HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                    .header(X_RAPIDAPI_KEY, apiFootballToken)
                    .header(HOST_NAME, HOST)
                    .queryString("league", 39)
                    .queryString("season", SEASON)
                    .queryString("from", LocalDate.now().minusDays(1L).toString())
                    .queryString("to", LocalDate.now().toString())
                    .asString();
            Root root = mapper.readValue(resp.getBody(), Root.class);
            List<Match> matches = root.getResponse().stream().map(this::getMatch)
                    .toList();
            matchService.updateAll(matches);
        }
    }

    private void matchInitFromApiFootball() throws JsonProcessingException {
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(X_RAPIDAPI_KEY, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", SEASON)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        List<Match> matches = root.getResponse().stream()
                .map(this::getMatch)
                .toList();
        matchService.save(matches);
    }

    private void matchDateTimeStatusUpdate() throws JsonProcessingException {
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(X_RAPIDAPI_KEY, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", SEASON)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        int currentWeekId = weekService.findCurrentWeek().getId();
        List<Match> matches = root.getResponse().stream()
                .filter(r -> isFutureMatch(currentWeekId, r))
                .map(this::getMatch)
                .toList();
        if (matches.isEmpty()) {
            serverLogger.info("No future matches to sync from API");
            return;
        }
        matchService.updateAll(matches);
        serverLogger.info("Synced {} match times from API (timezone={})", matches.size(), AppTimeZones.DISPLAY);
    }

    private boolean isFutureMatch(int currentWeekId, Response response) {
        int weekId = Integer.parseInt(response.getLeague().getRound().replaceAll("\\D+", ""));
        return weekId >= currentWeekId;
    }

    private Match getMatch(Response response) {
        Match match;

        int weekId = Integer.parseInt(response.getLeague().getRound().replaceAll("\\D+", ""));

        Fixture fixture = response.getFixture();
        int publicId = fixture.getPublicId();
        LocalDateTime matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(fixture.getTimestamp()),
                AppTimeZones.DISPLAY);
        String status = fixture.getStatus().getMyshort();
        switch (status) {
            case "PST" -> status = "pst";
            case "NS" -> status = "ns";
            case "FT" -> status = "ft";
            case "HT" -> status = "ht";
            case "1H", "2H" -> status = fixture.getStatus().getElapsed() + "'";
            default -> status = null;
        }

        ResponseTeam home = response.getTeams().getHome();
        ResponseTeam away = response.getTeams().getAway();

        int htPublicId = home.getId();
        int atPublicId = away.getId();

        if (response.getGoals().getHome() == null) {
            match = Match.builder()
                    .publicId(publicId)
                    .status(status)
                    .localDateTime(matchDateTime)
                    .weekId(weekId)
                    .homeTeamId(htPublicId)
                    .awayTeamId(atPublicId)
                    .build();
        } else {
            String result;
            Integer homeTeamScore = response.getGoals().getHome();
            Integer awayTeamScore = response.getGoals().getAway();
            if (homeTeamScore.equals(awayTeamScore)) {
                result = "D";
            } else {
                result = homeTeamScore > awayTeamScore ? "H" : "A";
            }
            match = Match.builder()
                    .publicId(publicId)
                    .status(status)
                    .localDateTime(matchDateTime)
                    .weekId(weekId)
                    .homeTeamId(htPublicId)
                    .awayTeamId(atPublicId)
                    .homeTeamScore(homeTeamScore)
                    .awayTeamScore(awayTeamScore)
                    .result(result)
                    .build();
        }
        return match;
    }

    private void postponedMatches() throws UnirestException, JsonProcessingException {
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(X_RAPIDAPI_KEY, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", SEASON)
                .queryString("status", "pst")
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        List<Match> matches = root.getResponse().stream()
                .map(this::getMatch)
                .toList();

        matchService.updateAll(matches);
    }

    public List<News> newsInit() throws IOException, ParseException, FeedException {
        List<News> news = new LinkedList<>();
        String title;
        String link;
        LocalDateTime dateTime;
        DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
        URL feedSource = new URL("https://www.sports.ru/stat/export/rss/taglenta.xml?id=1363805");
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(feedSource));
        List<SyndEntry> res = feed.getEntries().stream().limit(30L).toList();
        for (SyndEntry re : res) {
            link = re.getLink().replace("\n", "");
            title = re.getTitle().replace("\n", "")
                    .lines().filter(s -> !s.contains("?")).collect(Collectors.joining());
            dateTime = formatter.parse(re.getPublishedDate().toString())
                    .toInstant()
                    .atZone(AppTimeZones.DISPLAY)
                    .toLocalDateTime();
            if (!title.isEmpty()) {
                news.add(News.builder().title(title).link(link).localDateTime(dateTime).build());
            }
        }
        return news;
    }

    private void headToHeadInitFromApiFootball() throws UnirestException, JsonProcessingException {
        for (int league : List.of(39, 45, 48, 2, 40)) {
            for (int season : IntStream.rangeClosed(2020, SEASON).toArray()) {
                HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                        .header(X_RAPIDAPI_KEY, apiFootballToken)
                        .queryString("league", league)
                        .queryString("season", season)
                        .asString();
                Root root = mapper.readValue(resp.getBody(), Root.class);
                for (Response response : root.getResponse()) {
                    LocalDateTime matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(response.getFixture().getTimestamp()),
                            AppTimeZones.DISPLAY);
                    String leagueName = switch (response.getLeague().getName()) {
                        case "Premier League" -> "PL ";
                        case "League Cup" -> "LC ";
                        case "FA Cup" -> "FA ";
                        case "UEFA Champions League" -> "UCL ";
                        case "Championship" -> "CH ";
                        default -> response.getLeague().getName();
                    } + response.getLeague().getSeason();
                    Team homeTeam = DaoUtil.TEAMS.getOrDefault(response.getTeams().getHome().getId(), null);
                    Team awayTeam = DaoUtil.TEAMS.getOrDefault(response.getTeams().getAway().getId(), null);
                    if (homeTeam == null || awayTeam == null) {
                        continue;
                    }
                    if (response.getGoals().getHome() == null) {
                        continue;
                    }
                    Integer homeTeamScore = response.getGoals().getHome();
                    Integer awayTeamScore = response.getGoals().getAway();
                    HeadToHead headToHead = HeadToHead.builder()
                            .leagueName(leagueName)
                            .homeTeamId(homeTeam.getPublicId())
                            .awayTeamId(awayTeam.getPublicId())
                            .homeTeamScore(homeTeamScore)
                            .awayTeamScore(awayTeamScore)
                            .localDateTime(matchDateTime)
                            .build();
                    headToHeadService.save(headToHead);
                }
            }
        }
    }

    private void teamsInitFromApiFootball() throws UnirestException, JsonProcessingException {
        HttpResponse<String> resp = Unirest.get("https://v3.football.api-sports.io/teams")
                .header(X_RAPIDAPI_KEY, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", SEASON)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        for (Response response : root.getResponse()) {
            Team team = Team.builder()
                    .publicId(response.getTeam().getId())
                    .logo(response.getTeam().getLogo())
                    .name(response.getTeam().getName())
                    .code(response.getTeam().getCode())
                    .build();
            teamService.save(team);
        }
    }
}
