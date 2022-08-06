package zhigalin.predictions.util.init_data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.HeadToHeadMapper;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.RoleService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.model.EPLInfoBot;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


@Service
@NoArgsConstructor
public class DataInitServiceImpl {
    private static final String X_RAPID_API = "x-rapidapi-key";
    @Value("${api.football.token}")
    private String API_FOOTBALL_TOKEN;
    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH); //"2021-08-13T19:00:00+00:00"

    private Long id;

    private Long publicId;
    private Week week;
    private LocalDateTime matchDateTime;
    private Team homeTeam;
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String result;
    private String status;
    private Match match;
    private Role admin;
    private Role user;
    private String leagueName;

    private UserService userService;
    private TeamService teamService;
    private SeasonService seasonService;
    private WeekService weekService;
    private MatchService matchService;
    private PredictionService predictionService;
    private RoleService roleService;
    private StandingService standingService;
    private NewsService newsService;
    private HeadToHeadService headToHeadService;

    @Autowired
    private SeasonMapper seasonMapper;
    @Autowired
    private StandingMapper standingMapper;
    @Autowired
    private WeekMapper weekMapper;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private HeadToHeadMapper headToHeadMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    PasswordEncoder bCryptPasswordEncoder;
    @Autowired
    EPLInfoBot eplInfoBot;

    @Autowired
    public DataInitServiceImpl(UserService userService, TeamService teamService, SeasonService seasonService,
                               WeekService weekService, MatchService matchService, PredictionService predictionService,
                               RoleService roleService, StandingService standingService, NewsService newsService,
                               HeadToHeadService headToHeadService) {
        this.userService = userService;
        this.teamService = teamService;
        this.seasonService = seasonService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.roleService = roleService;
        this.standingService = standingService;
        this.newsService = newsService;
        this.headToHeadService = headToHeadService;
    }

    public void allInit() {
        //predictInit();
        //fastStatsInit();
        //roleInit();
        //userInit();
        //teamsInitFromApiFootball();
        //matchInitFromApiFootball();
        //currentWeekUpdate();
        //standingInitFromApiFootball();

        //headToHeadInitFromApiFootball();
        //statsUpdate();
        Thread run = new Thread(() -> {
            while (true) {
                try {
                    //matchUpdate();
                    matchUpdateFromApiFootball();
                    newsInit();
                    Thread.sleep(240000); //1000 - 1 сек
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        run.start();
    }

    @SneakyThrows
    private void headToHeadInitFromApiFootball() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        File dir = getFileFromResource("static/json");
        for (File file : dir.listFiles()) {
            JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/" + file.getName()))), JsonElement.class);
            JsonArray responses = mainObj.getAsJsonArray("response");
            for (JsonElement response : responses) {
                JsonObject matchObj = response.getAsJsonObject();

                JsonObject fixture = matchObj.getAsJsonObject("fixture");
                matchDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(fixture.get("timestamp").getAsLong()),
                        TimeZone.getDefault().toZoneId());

                JsonObject league = matchObj.getAsJsonObject("league");
                leagueName = switch (league.get("name").getAsString()) {
                    case "Premier League" -> "PL ";
                    case "League Cup" -> "LC ";
                    case "FA Cup" -> "FA ";
                    case "UEFA Champions League" -> "UCL ";
                    case "Championship" -> "C ";
                    default -> league.get("name").getAsString();
                } + league.get("season").getAsString();

                JsonObject teams = matchObj.getAsJsonObject("teams");
                JsonObject home = teams.getAsJsonObject("home");
                JsonObject away = teams.getAsJsonObject("away");
                homeTeam = teamMapper.toEntity(teamService.getByPublicId(home.get("id").getAsLong()));
                awayTeam = teamMapper.toEntity(teamService.getByPublicId(away.get("id").getAsLong()));

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
    private void currentWeekUpdate() {
        HttpResponse<JsonNode> response = Unirest.get("https://v3.football.api-sports.io/fixtures/rounds")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .queryString("league", 39)
                .queryString("season", 2022)
                .queryString("current", true)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray responseArr = mainObj.getAsJsonArray("response");

        List<Week> weeks = weekService.getAll().stream().map(weekMapper::toEntity).toList();

        for (Week week : weeks) {
            week.setIsCurrent(week.getId().equals(Long.valueOf(responseArr.get(0).toString().replaceAll("\\D+", ""))));
            weekService.save(weekMapper.toDto(week));
        }
    }

    @SneakyThrows
    private void standingInitFromApiFootball() {
        HttpResponse<JsonNode> response = Unirest.get("https://v3.football.api-sports.io/standings")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .queryString("league", 39)
                .queryString("season", 2022)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        /*Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/standings.json"))), JsonElement.class);*/

        JsonArray responseArr = mainObj.getAsJsonArray("response");
        JsonObject singleObject = responseArr.get(0).getAsJsonObject();
        JsonObject league = singleObject.getAsJsonObject("league");

        JsonArray standings = league.getAsJsonArray("standings");
        JsonArray stand = standings.get(0).getAsJsonArray();
        for (JsonElement standing : stand) {
            JsonObject obj = standing.getAsJsonObject();
            JsonObject team = obj.getAsJsonObject("team");
            JsonObject all = obj.getAsJsonObject("all");
            JsonObject goals = all.getAsJsonObject("goals");

            Standing std = Standing.builder()
                    .team(teamMapper.toEntity(teamService.getByPublicId(team.get("id").getAsLong())))
                    .points(obj.get("points").getAsInt())
                    .result((obj.get("description").isJsonNull()) ? null : obj.get("description").getAsString())
                    .games(all.get("played").getAsInt())
                    .won(all.get("win").getAsInt())
                    .draw(all.get("draw").getAsInt())
                    .lost(all.get("lose").getAsInt())
                    .goalsScored(goals.get("for").getAsInt())
                    .goalsAgainst(goals.get("against").getAsInt())
                    .build();
            standingService.save(standingMapper.toDto(std));
        }
    }

    @SneakyThrows
    private void matchUpdateFromApiFootball() {
        List<MatchDto> today = matchService.getAllByTodayDate();
        if (today.isEmpty() || today.get(0).getLocalDateTime().minusMinutes(10).isAfter(LocalDateTime.now()) ||
                today.get(today.size() - 1).getLocalDateTime().plusHours(2).plusMinutes(10).isBefore(LocalDateTime.now())) {
            return;
        }
        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header("x-rapidapi-host", "v3.football.api-sports.io")
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
                case "PST" -> {
                    continue;
                }
                case "FT" -> status = "ft";
                case "HT" -> status = "ht";
                case "1H", "2H" -> status = "live" + " " + fixtureStatus.get("elapsed").toString() + "'";
                default -> status = "-";
            }

            JsonObject league = matchObj.getAsJsonObject("league");

            JsonObject teams = matchObj.getAsJsonObject("teams");
            JsonObject home = teams.getAsJsonObject("home");
            JsonObject away = teams.getAsJsonObject("away");
            homeTeam = teamMapper.toEntity(teamService.getByPublicId(home.get("id").getAsLong()));
            awayTeam = teamMapper.toEntity(teamService.getByPublicId(away.get("id").getAsLong()));

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
    private void matchInitFromApiFootball() {

        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header("x-rapidapi-host", "v3.football.api-sports.io")
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
                default -> status = "-";
            }

            JsonObject league = matchObj.getAsJsonObject("league");

            seasonService.saveSeason(seasonMapper.toDto(Season.builder().seasonName(league.get("season").getAsString()).build()));

            week = Week.builder().weekName("week " + league.get("round").toString().replaceAll("\\D+", ""))
                    .season(seasonMapper.toEntity(seasonService.getById(1L)))
                    .build();
            WeekDto weekDto = weekService.save(weekMapper.toDto(week));

            JsonObject teams = matchObj.getAsJsonObject("teams");
            JsonObject home = teams.getAsJsonObject("home");
            JsonObject away = teams.getAsJsonObject("away");
            homeTeam = teamMapper.toEntity(teamService.getByPublicId(home.get("id").getAsLong()));
            awayTeam = teamMapper.toEntity(teamService.getByPublicId(away.get("id").getAsLong()));

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
        /*Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/teams.json"))), JsonElement.class);
        JsonArray responses = mainObj.getAsJsonArray("response");*/

        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/teams")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .header("x-rapidapi-host", "v3.football.api-sports.io")
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
            teamService.saveTeam(teamMapper.toDto(teamEntity));
        }
    }

    @SneakyThrows
    private void newsInit() {
        if (newsService.getAll().size() > 150) {
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
        List<SyndEntry> res = feed.getEntries();

        for (Object re : res) {
            link = ((SyndEntryImpl) re).getLink().replaceAll("\n", "");
            title = ((SyndEntryImpl) re).getTitle().replace("\n", "");
            dateTime = formatter.parse(((SyndEntryImpl) re).getPublishedDate().toString())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            newsService.save(NewsDto.builder().title(title).link(link).dateTime(dateTime).build());
        }
    }

    private void roleInit() {
        admin = Role.builder().role("ADMIN").build();
        user = Role.builder().role("USER").build();
        roleService.save(roleMapper.toDto(admin));
        roleService.save(roleMapper.toDto(user));
    }

    private void predictInit() {
        for (int i = 0; i < 100; i++) {
            Long matchId = (long) new Random().nextInt(380);
            Integer htScore = new Random().nextInt(5);
            Integer atScore = new Random().nextInt(5);
            Long userId = (long) (1 + new Random().nextInt(4));
            predictionService.save(PredictionDto.builder()
                    .match(matchMapper.toEntity(matchService.getById(matchId)))
                    .awayTeamScore(atScore)
                    .homeTeamScore(htScore)
                    .user(userMapper.toEntity(userService.getById(userId)))
                    .build());
        }
    }

    private void userInit() {
        for (int i = 1; i < 5; i++) {
            Role role = roleMapper.toEntity(roleService.findById((long) 1));
            UserDto userDto = UserDto.builder().login("user" + i).password("user" + i)
                    .roles(Collections.singleton(role)).build();
            userService.saveUser(userDto);
        }
    }

    private File getFileFromResource(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return new File(resource.getFile());
            //return new File(resource.toURI());
        }
    }
}
