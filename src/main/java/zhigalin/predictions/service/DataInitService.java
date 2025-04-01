package zhigalin.predictions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
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
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
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
    private static final String X_RAPIDAPI_KEY = "x-rapidapi-key";
    private static final String HOST_NAME = "x-rapidapi-host";
    private static final String HOST = "v3.football.api-sports.io";
    private static final String FIXTURES_URL = "https://v3.football.api-sports.io/fixtures";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    public DataInitService(TeamService teamService, WeekService weekService, MatchService matchService,
                           HeadToHeadService headToHeadService, NotificationService notificationService,
                           PanicSender panicSender
    ) {
        this.teamService = teamService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.notificationService = notificationService;
        this.panicSender = panicSender;
    }

    //        @Scheduled(initialDelay = 1000, fixedDelay = 5000000)
    @Scheduled(cron = "0 */6 * * * *")
    private void start() {
        try {
            serverLogger.info("Data init start");
            matchUpdateFromApiFootball();
            notificationService.check();
        } catch (Exception e) {
            panicSender.sendPanic("Main method", e);
        }
    }

    @Scheduled(cron = "0 50 8 * * *")
    private void matchUpdate() throws JsonProcessingException {
        matchDateTimeStatusUpdate();
    }

    private void matchUpdateFromApiFootball() throws JsonProcessingException {
        if (matchService.findAllByCurrentWeek().stream()
                .allMatch(m -> Objects.equals(m.getStatus(), "ft")
                               || Objects.equals(m.getStatus(), "pst"))) {
            notificationService.weeklyResultNotification();
            weekService.updateCurrent();
        }
        if (!matchService.findOnlineMatches().isEmpty()) {
            serverLogger.info("Matches to update found");
            HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                    .header(X_RAPIDAPI_KEY, apiFootballToken)
                    .header(HOST_NAME, HOST)
                    .queryString("league", 39)
                    .queryString("season", 2024)
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
                .queryString("season", 2024)
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
                .queryString("season", 2024)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        int currentWeekId = weekService.findCurrentWeek().getId();
        List<Match> matches = root.getResponse().stream()
                .filter(r -> isFutureMatch(currentWeekId, r))
                .map(this::getMatch)
                .toList();
        matchService.updateAll(matches);
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
                TimeZone.getDefault().toZoneId());
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
                .queryString("season", 2024)
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
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            if (!title.isEmpty()) {
                news.add(News.builder().title(title).link(link).localDateTime(dateTime).build());
            }
        }
        return news;
    }

    private void headToHeadInitFromApiFootball() throws UnirestException, JsonProcessingException {
        List<Integer> leagues = Stream.of(39, 45, 48, 2).toList();
        List<Integer> seasons = Stream.of(2020, 2021, 2022, 2023).toList();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                        .header(X_RAPIDAPI_KEY, apiFootballToken)
                        .queryString("league", leagues.get(i))
                        .queryString("season", seasons.get(j))
                        .asString();
                Root root = mapper.readValue(resp.getBody(), Root.class);
                for (Response response : root.getResponse()) {
                    LocalDateTime matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(response.getFixture().getTimestamp()),
                            TimeZone.getDefault().toZoneId());
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
                    serverLogger.info("Saved head to head: {}", headToHead);
                }
            }
        }
    }

    private void teamsInitFromApiFootball() throws UnirestException, JsonProcessingException {
        HttpResponse<String> resp = Unirest.get("https://v3.football.api-sports.io/teams")
                .header(X_RAPIDAPI_KEY, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2024)
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
