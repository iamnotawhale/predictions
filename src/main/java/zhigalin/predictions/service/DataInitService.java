package zhigalin.predictions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.input.Fixture;
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.ResponseTeam;
import zhigalin.predictions.model.input.Root;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.user.UserService;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class DataInitService {
    @Value("${x.rapid.api}")
    private String xRapidApi;
    @Value("${api.football.token}")
    private String apiFootballToken;
    @Value("${bot.chatId}")
    private String chatId;
    @Value("${bot.url}")
    private String url;

    private final TeamService teamService;
    private final SeasonService seasonService;
    private final WeekService weekService;
    private final MatchService matchService;
    private final NewsService newsService;
    private final HeadToHeadService headToHeadService;
    private final StandingService standingService;
    private final UserService userService;
    private List<Match> online;
    private final Set<Long> notificationBan = new HashSet<>();
    private static final String HOST_NAME = "x-rapidapi-host";
    private static final String HOST = "v3.football.api-sports.io";
    private static final String FIXTURES_URL = "https://v3.football.api-sports.io/fixtures";
    private static final ObjectMapper mapper = new ObjectMapper();

    public void allInit() {
//        LocalTime now = LocalTime.now();
//        online = matchService.findOnline();
//        if (now.isAfter(LocalTime.of(9, 0)) &&
//                now.isBefore(LocalTime.of(9, 6))) {
//            sendTodaysMatchNotification();
//        }
//        matchUpdateFromApiFootball();
//        newsInit();
//        fullTimeMatchNotification();
        teamsInitFromApiFootball();
        matchInitFromApiFootball();
//        headToHeadInitFromApiFootball();
    }

    @SneakyThrows
    private void matchUpdateFromApiFootball() {
        Match match;
        String result;
        if (matchService.findAllByCurrentWeek().stream()
                .allMatch(m -> Objects.equals(m.getStatus(), "ft")
                        || Objects.equals(m.getStatus(), "pst"))) {
            currentWeekUpdate();
            matchDateTimeStatusUpdate();
        }
        if (!online.isEmpty()) {
            HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                    .header(xRapidApi, apiFootballToken)
                    .header(HOST_NAME, HOST)
                    .queryString("league", 39)
                    .queryString("season", 2023)
                    .queryString("from", LocalDate.now().toString())
                    .queryString("to", LocalDate.now().toString())
                    .asString();
            Root root = mapper.readValue(resp.getBody(), Root.class);
            for (Response response : root.getResponse()) {
                Fixture fixture = response.getFixture();
                String status = fixture.getStatus().getMyshort();
                switch (status) {
                    case "PST" -> status = "pst";
                    case "NS" -> status = "ns";
                    case "FT" -> status = "ft";
                    case "HT" -> status = "ht";
                    case "1H", "2H" -> status = fixture.getStatus().getElapsed() + "'";
                    default -> status = null;
                }
                if (response.getGoals().getHome() == null) {
                    continue;
                } else {
                    Integer homeTeamScore = response.getGoals().getHome();
                    Integer awayTeamScore = response.getGoals().getAway();

                    if (homeTeamScore.equals(awayTeamScore)) {
                        result = "D";
                    } else {
                        result = homeTeamScore > awayTeamScore ? "H" : "A";
                    }
                    match = new Match();
                    match.setPublicId(fixture.getId());
                    match.setStatus(status);
                    match.setHomeTeamScore(homeTeamScore);
                    match.setAwayTeamScore(awayTeamScore);
                    match.setResult(result);
                }
                matchService.update(match);
            }
        }
    }

    @SneakyThrows
    private void matchInitFromApiFootball() {
        Match match;
        String result;
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(xRapidApi, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2023)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        for (Response response : root.getResponse()) {
            Fixture fixture = response.getFixture();
            Long publicId = fixture.getId();
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
            Season season;
            if (seasonService.findByName(response.getLeague().getSeason().toString()) == null) {
                season = seasonService.save(
                        Season.builder()
                                .name(response.getLeague().getSeason().toString())
                                .build()
                );
                log.info("Season " + season + " saved");
            } else {
                season = seasonService.findByName(response.getLeague().getSeason().toString());
            }

            long weekId = Long.parseLong(response.getLeague().getRound().replaceAll("\\D+", ""));
            Week week;
            if (weekService.findById(weekId) == null) {
                String weekName = "week " + weekId;
                week = weekService.save(Week.builder().name(weekName)
                        .isCurrent(weekName.equals("week 1"))
                        .season(season)
                        .build());
                log.info("Week " + week + " saved");
            } else {
                week = weekService.findById(weekId);
            }

            ResponseTeam home = response.getTeams().getHome();
            ResponseTeam away = response.getTeams().getAway();

            Long htpid = home.getId();
            Long atpid = away.getId();

            Team homeTeam;
            if (teamService.findByPublicId(htpid) == null) {
                homeTeam = Team.builder()
                        .publicId(home.getId())
                        .logo(home.getLogo())
                        .name(home.getName())
                        .code(home.getCode())
                        .build();
                teamService.save(homeTeam);
            } else {
                homeTeam = teamService.findByPublicId(htpid);
            }
            Team awayTeam;
            if (teamService.findByPublicId(atpid) == null) {
                awayTeam = Team.builder()
                        .publicId(away.getId())
                        .logo(away.getLogo())
                        .name(away.getName())
                        .code(away.getCode())
                        .build();
                teamService.save(awayTeam);
            } else {
                awayTeam = teamService.findByPublicId(atpid);
            }


            if (standingService.findByPublicId(htpid) == null) {
                Standing st = Standing.builder()
                        .games(0)
                        .points(0)
                        .won(0)
                        .draw(0)
                        .lost(0)
                        .team(homeTeam)
                        .goalsScored(0)
                        .goalsAgainst(0)
                        .build();
                standingService.save(st);
            }

            if (standingService.findByPublicId(atpid) == null) {
                Standing st = Standing.builder()
                        .games(0)
                        .points(0)
                        .won(0)
                        .draw(0)
                        .lost(0)
                        .team(awayTeam)
                        .goalsScored(0)
                        .goalsAgainst(0)
                        .build();
                standingService.save(st);
            }

            if (response.getGoals().getHome() == null) {
                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .week(week)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .build();
            } else {
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
                        .week(week)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .build();
            }
            matchService.save(match);
            log.info("Match " + match + " saved");
        }
    }

    @SneakyThrows
    private void matchDateTimeStatusUpdate() {
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(xRapidApi, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2023)
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        for (Response response : root.getResponse()) {
            Fixture fixture = response.getFixture();
            Long publicId = fixture.getId();
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
            Match match = new Match();
            match.setPublicId(publicId);
            match.setStatus(status);
            match.setLocalDateTime(matchDateTime);
            matchService.updateStatusAndLocalDateTime(match);
        }
    }

    private void sendTodaysMatchNotification() {
        Long tour = null;
        List<Match> todayMatches = matchService.findAllByTodayDate();
        StringBuilder builder = new StringBuilder();
        if (!todayMatches.isEmpty()) {
            builder.append("`").append("МАТЧИ СЕГОДНЯ").append("`").append("\n\n");
            for (Match match : todayMatches) {
                builder.append("`");
                if (!match.getWeek().getId().equals(tour)) {
                    builder.append(match.getWeek().getId()).append(" тур").append("\n");
                    tour = match.getWeek().getId();
                }
                builder.append(match.getHomeTeam().getCode()).append(" ");
                if (!Objects.equals(match.getStatus(), "ns") && !Objects.equals(match.getStatus(), "pst")) {
                    builder.append(match.getHomeTeamScore()).append(" - ")
                            .append(match.getAwayTeamScore()).append(" ")
                            .append(match.getAwayTeam().getCode()).append(" ")
                            .append(match.getStatus()).append(" ");
                } else if (Objects.equals(match.getStatus(), "pst")) {
                    builder.append("- ").append(match.getAwayTeam().getCode())
                            .append(" ⏰ ").append(match.getStatus());
                } else {
                    builder.append("- ").append(match.getAwayTeam().getCode())
                            .append(" ⏱ ").append(match.getLocalDateTime().toLocalTime());
                }
                builder.append("`").append("\n");
            }
            try {
                HttpResponse<JsonNode> response = Unirest.get(url)
                        .queryString("chat_id", chatId)
                        .queryString("text", builder.toString())
                        .queryString("parse_mode", "Markdown")
                        .asJson();
                if (response.getStatus() == 200) {
                    log.info(response.getBody());
                    log.info("Message todays match notification has been send");
                } else {
                    log.warn("Don't send todays match notification");
                }
            } catch (UnirestException e) {
                log.error("Sending message error: " + e.getMessage());
            }
        }
    }

    private void fullTimeMatchNotification() {
        List<Match> online = matchService.findOnline();
        if (!online.isEmpty()) {
            for (Match match : online) {
                StringBuilder builder = new StringBuilder();
                if (match.getStatus().equals("ft") && !notificationBan.contains(match.getPublicId())) {
                    builder.append("`").append(match.getHomeTeam().getCode()).append(" ")
                            .append(match.getHomeTeamScore()).append(" ")
                            .append(match.getStatus()).append(" ")
                            .append(match.getAwayTeamScore()).append(" ")
                            .append(match.getAwayTeam().getCode()).append("`").append("\n\n");
                    for (Prediction prediction : match.getPredictions()) {
                        builder.append("`").append(prediction.getUser().getLogin().substring(0, 4).toUpperCase()).append(" ")
                                .append(prediction.getHomeTeamScore()).append(":").append(prediction.getAwayTeamScore()).append(" ");
                        if (prediction.getPoints() != -1) {
                            builder.append(" ");
                        }
                        builder.append(prediction.getPoints()).append(" PTS").append("`").append("\n");
                    }
                    notificationBan.add(match.getPublicId());
                    try {
                        HttpResponse<String> response = Unirest.get(url)
                                .queryString("chat_id", chatId)
                                .queryString("text", builder.toString())
                                .queryString("parse_mode", "Markdown")
                                .asString();
                        if (response.getStatus() == 200) {
                            log.info(response.getBody());
                            log.info("Message has been send");
                        } else {
                            log.warn("Don't send full-time notification" + response.getBody());
                        }
                    } catch (UnirestException e) {
                        log.error("Sending message error: " + e.getMessage());
                    }
                }
            }
        } else {
            if (!notificationBan.isEmpty()) {
                notificationBan.clear();
            }
        }
    }

    @SneakyThrows
    private void postponedMatches() {
        HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                .header(xRapidApi, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2023)
                .queryString("status", "pst")
                .asString();
        Root root = mapper.readValue(resp.getBody(), Root.class);
        for (Response response : root.getResponse()) {
            Match match = matchService.findByPublicId(response.getFixture().getId());
            match.setStatus("pst");
            matchService.save(match);
        }
    }

    @SneakyThrows
    private void currentWeekUpdate() {
        Long id = weekService.findCurrentWeek().getId();
        Week currentWeek = weekService.findById(id);
        Week nextCurrentWeek = weekService.findById(id + 1);
        weekService.updateCurrent(currentWeek, false);
        weekService.updateCurrent(nextCurrentWeek, true);
    }

    @SneakyThrows
    private void newsInit() {
        if (newsService.findAll().size() > 30) {
            newsService.deleteAll();
        }
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
            if (title.length() > 0) {
                newsService.save(News.builder().title(title).link(link).localDateTime(dateTime).build());
            }
        }
    }

    @SneakyThrows
    private void headToHeadInitFromApiFootball() {
        List<Integer> leagues = Stream.of(39, 45, 48, 2).toList();
        List<Integer> seasons = Stream.of(2021, 2022).toList();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                HttpResponse<String> resp = Unirest.get(FIXTURES_URL)
                        .header(xRapidApi, apiFootballToken)
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
                    Team homeTeam = teamService.findByPublicId(response.getTeams().getHome().getId());
                    Team awayTeam = teamService.findByPublicId(response.getTeams().getAway().getId());
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
                            .homeTeam(homeTeam)
                            .awayTeam(awayTeam)
                            .homeTeamScore(homeTeamScore)
                            .awayTeamScore(awayTeamScore)
                            .localDateTime(matchDateTime)
                            .build();
                    headToHeadService.save(headToHead);
                }
            }
        }
    }

    @SneakyThrows
    private void teamsInitFromApiFootball() {
        HttpResponse<String> resp = Unirest.get("https://v3.football.api-sports.io/teams")
                .header(xRapidApi, apiFootballToken)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2023)
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
