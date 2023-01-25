package zhigalin.predictions.service._impl.init;

import com.google.gson.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.HeadToHeadMapper;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.init.DataInitService;
import zhigalin.predictions.service.news.NewsService;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
public class DataInitServiceImpl implements DataInitService {

    @Value("${x.rapid.api}")
    private String X_RAPID_API;
    @Value("${api.football.token}")
    private String API_FOOTBALL_TOKEN;

    private static final String HOST_NAME = "x-rapidapi-host";

    private static final String HOST = "v3.football.api-sports.io";
    private Long publicId;
    private LocalDateTime matchDateTime;
    private Team homeTeam;
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String result;
    private String status;
    private Match match;

    private final TeamService teamService;
    private final SeasonService seasonService;
    private final WeekService weekService;
    private final MatchService matchService;
    private final NewsService newsService;
    private final HeadToHeadService headToHeadService;
    private final SeasonMapper seasonMapper;
    private final WeekMapper weekMapper;
    private final TeamMapper teamMapper;
    private final MatchMapper matchMapper;
    private final HeadToHeadMapper headToHeadMapper;

    public void allInit() {
        matchUpdateFromApiFootball();
        newsInit();

    }

    @SneakyThrows
    private void postponedMatches() {
        List<MatchDto> pst = matchService.findAllByStatus("pst");

        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2022)
                .queryString("status", "pst")
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responses = mainObj.getAsJsonArray("response");

        for (JsonElement response : responses) {
            JsonObject matchObj = response.getAsJsonObject();
            JsonObject fixture = matchObj.getAsJsonObject("fixture");

            MatchDto matchDto = matchService.findByPublicId(fixture.get("id").getAsLong());
            match.setStatus("pst");
            matchService.save(matchDto);
        }
    }

    @SneakyThrows
    private void matchUpdateFromApiFootball() {
        if (matchService.findAllByCurrentWeek().stream()
                .allMatch(match -> Objects.equals(match.getStatus(), "ft")
                        || Objects.equals(match.getStatus(), "pst"))) {
            currentWeekUpdate();
            matchDateTimeStatusUpdate();
        }
        if (matchService.findOnline().isEmpty()) {
            return;
        }
        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2022)
                .queryString("from", LocalDateTime.now().toLocalDate().toString())
                .queryString("to", LocalDateTime.now().toLocalDate().toString())
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responses = mainObj.getAsJsonArray("response");

        for (JsonElement response : responses) {
            JsonObject matchObj = response.getAsJsonObject();
            JsonObject fixture = matchObj.getAsJsonObject("fixture");
            publicId = fixture.get("id").getAsLong();

            JsonObject fixtureStatus = fixture.get("status").getAsJsonObject();
            status = fixtureStatus.get("short").getAsString();

            switch (status) {
                case "PST" -> status = "pst";
                case "NS" -> status = "ns";
                case "FT" -> status = "ft";
                case "HT" -> status = "ht";
                case "1H", "2H" -> status = fixtureStatus.get("elapsed").toString() + "'";
                default -> status = null;
            }

            JsonObject teams = matchObj.getAsJsonObject("teams");
            JsonObject home = teams.getAsJsonObject("home");
            JsonObject away = teams.getAsJsonObject("away");
            homeTeam = teamMapper.toEntity(teamService.findByPublicId(home.get("id").getAsLong()));
            awayTeam = teamMapper.toEntity(teamService.findByPublicId(away.get("id").getAsLong()));

            JsonObject goals = matchObj.getAsJsonObject("goals");

            if (goals.get("home").isJsonNull()) {
                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .build();
            } else {
                homeTeamScore = goals.get("home").getAsInt();
                awayTeamScore = goals.get("away").getAsInt();

                result = (homeTeamScore.equals(awayTeamScore)) ? "D" : (homeTeamScore > awayTeamScore) ? "H" : "A";

                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }

    @SneakyThrows
    private void currentWeekUpdate() {
        Long id = weekService.findCurrentWeek().getId();
        WeekDto currentWeek = weekService.findById(id);
        WeekDto nextCurrentWeek = weekService.findById(id + 1);
        currentWeek.setIsCurrent(false);
        nextCurrentWeek.setIsCurrent(true);
        weekService.save(currentWeek);
        weekService.save(nextCurrentWeek);
    }

    @SneakyThrows
    private void newsInit() {
        if (newsService.findAll().size() > 30) {
            newsService.deleteAll();
            newsService.resetSequence();
        }

        String title;
        String link;
        LocalDateTime dateTime;
        DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);

        URL feedSource = new URL("https://www.sports.ru/stat/export/rss/taglenta.xml?id=1363805");
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(feedSource));
        List<SyndEntry> res = feed.getEntries().stream().limit(30L).toList();

        for (Object re : res) {
            link = ((SyndEntryImpl) re).getLink().replaceAll("\n", "");
            title = ((SyndEntryImpl) re).getTitle().replace("\n", "").lines().filter(s -> !s.contains("?")).collect(Collectors.joining());
            dateTime = formatter.parse(((SyndEntryImpl) re).getPublishedDate().toString())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            newsService.save(NewsDto.builder().title(title).link(link).dateTime(dateTime).build());
        }
    }

    @SneakyThrows
    private void headToHeadInitFromApiFootball() {

        List<Integer> leagues = Stream.of(39, 45, 48, 2).toList();

        for (int i = 0; i < 4; i++) {
            HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                    .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                    .queryString("league", leagues.get(i))
                    .queryString("season", 2022)
                    .asJson();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            //Create data array from api
            JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();

            JsonArray responses = mainObj.getAsJsonArray("response");
            for (JsonElement response : responses) {
                JsonObject matchObj = response.getAsJsonObject();

                JsonObject fixture = matchObj.getAsJsonObject("fixture");
                matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(fixture.get("timestamp").getAsLong()),
                        TimeZone.getDefault().toZoneId());

                JsonObject league = matchObj.getAsJsonObject("league");
                String leagueName = switch (league.get("name").getAsString()) {
                    case "Premier League" -> "PL ";
                    case "League Cup" -> "LC ";
                    case "FA Cup" -> "FA ";
                    case "UEFA Champions League" -> "UCL ";
                    case "Championship" -> "CH ";
                    default -> league.get("name").getAsString();
                } + league.get("season").getAsString();

                JsonObject teams = matchObj.getAsJsonObject("teams");
                JsonObject home = teams.getAsJsonObject("home");
                JsonObject away = teams.getAsJsonObject("away");
                homeTeam = teamMapper.toEntity(teamService.findByPublicId(home.get("id").getAsLong()));
                awayTeam = teamMapper.toEntity(teamService.findByPublicId(away.get("id").getAsLong()));

                if (homeTeam == null || awayTeam == null) {
                    continue;
                }

                JsonObject goals = matchObj.getAsJsonObject("goals");

                if (goals.get("home").isJsonNull()) {
                    continue;
                }
                homeTeamScore = goals.get("home").getAsInt();
                awayTeamScore = goals.get("away").getAsInt();

                HeadToHead headToHead = HeadToHead.builder()
                        .leagueName(leagueName)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .localDateTime(matchDateTime)
                        .build();
                headToHeadService.save(headToHeadMapper.toDto(headToHead));
            }
        }
    }

    @SneakyThrows
    private void matchDateTimeStatusUpdate() {
        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2022)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responses = mainObj.getAsJsonArray("response");

        for (JsonElement response : responses) {
            JsonObject matchObj = response.getAsJsonObject();
            JsonObject fixture = matchObj.getAsJsonObject("fixture");
            matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(fixture.get("timestamp").getAsLong()),
                    TimeZone.getDefault().toZoneId());
            JsonObject fixtureStatus = fixture.get("status").getAsJsonObject();
            status = fixtureStatus.get("short").getAsString();

            switch (status) {
                case "PST" -> status = "pst";
                case "NS" -> status = "ns";
                case "FT" -> status = "ft";
                case "HT" -> status = "ht";
                case "1H", "2H" -> status = fixtureStatus.get("elapsed").toString() + "'";
                default -> status = null;
            }

            JsonObject teams = matchObj.getAsJsonObject("teams");
            JsonObject home = teams.getAsJsonObject("home");
            JsonObject away = teams.getAsJsonObject("away");
            homeTeam = teamMapper.toEntity(teamService.findByPublicId(home.get("id").getAsLong()));
            awayTeam = teamMapper.toEntity(teamService.findByPublicId(away.get("id").getAsLong()));

            JsonObject goals = matchObj.getAsJsonObject("goals");
            if (goals.get("home").isJsonNull()) {
                match = Match.builder()
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .build();
            } else {
                homeTeamScore = goals.get("home").getAsInt();
                awayTeamScore = goals.get("away").getAsInt();

                result = (homeTeamScore.equals(awayTeamScore)) ? "D" : (homeTeamScore > awayTeamScore) ? "H" : "A";

                match = Match.builder()
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }

    }

    @SneakyThrows
    private void matchInitFromApiFootball() {

        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2022)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responses = mainObj.getAsJsonArray("response");

        for (JsonElement response : responses) {
            JsonObject matchObj = response.getAsJsonObject();
            JsonObject fixture = matchObj.getAsJsonObject("fixture");
            publicId = fixture.get("id").getAsLong();
            matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(fixture.get("timestamp").getAsLong()),
                    TimeZone.getDefault().toZoneId());
            JsonObject fixtureStatus = fixture.get("status").getAsJsonObject();
            status = fixtureStatus.get("short").getAsString();

            switch (status) {
                case "PST" -> {
                    continue;
                }
                case "FT" -> status = "ft";
                case "HT" -> status = "ht";
                case "1H", "2H" -> status = "live" + " " + fixtureStatus.get("elapsed").toString() + "'";
                default -> status = null;
            }

            JsonObject league = matchObj.getAsJsonObject("league");

            seasonService.save(seasonMapper.toDto(Season.builder().seasonName(league.get("season").getAsString()).build()));

            Week week = Week.builder().weekName("week " + league.get("round").toString().replaceAll("\\D+", ""))
                    .season(seasonMapper.toEntity(seasonService.findById(1L)))
                    .build();
            WeekDto weekDto = weekService.save(weekMapper.toDto(week));

            JsonObject teams = matchObj.getAsJsonObject("teams");
            JsonObject home = teams.getAsJsonObject("home");
            JsonObject away = teams.getAsJsonObject("away");
            homeTeam = teamMapper.toEntity(teamService.findByPublicId(home.get("id").getAsLong()));
            awayTeam = teamMapper.toEntity(teamService.findByPublicId(away.get("id").getAsLong()));

            JsonObject goals = matchObj.getAsJsonObject("goals");
            if (goals.get("home").isJsonNull()) {
                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .build();
            } else {
                homeTeamScore = goals.get("home").getAsInt();
                awayTeamScore = goals.get("away").getAsInt();

                result = (homeTeamScore.equals(awayTeamScore)) ? "D" : (homeTeamScore > awayTeamScore) ? "H" : "A";

                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }

    @SneakyThrows
    private void teamsInitFromApiFootball() {
        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/teams")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header(HOST_NAME, HOST)
                .queryString("league", 39)
                .queryString("season", 2022)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responses = mainObj.getAsJsonArray("response");

        for (JsonElement response : responses) {
            JsonObject teamObj = response.getAsJsonObject();
            JsonObject team = teamObj.getAsJsonObject("team");
            Team teamEntity = Team.builder()
                    .publicId(team.get("id").getAsLong())
                    .logo(team.get("logo").getAsString())
                    .teamName(team.get("name").getAsString())
                    .code(team.get("code").getAsString())
                    .build();
            teamService.save(teamMapper.toDto(teamEntity));
        }
    }
}
