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
import zhigalin.predictions.converter.event.*;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.event.StatsDto;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.event.*;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.service.event.*;
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

    //    private static final String SEASON_INFO = "https://app.sportdataapi.com/api/v1/soccer/seasons";
//    private static final String DATA_URL = "https://app.sportdataapi.com/api/v1/soccer/matches";
//    private static final String STANDING = "https://app.sportdataapi.com/api/v1/soccer/standings";
//    private static final String STATS = "https://app.sportdataapi.com/api/v1/soccer/matches";
//    private static final String WEEK = "https://app.sportdataapi.com/api/v1/soccer/rounds";
//    private static final String TOKEN = "577b6040-b8e8-11ec-804e-414c550f7861"; /*"8002bee0-9cd1-11ec-9d81-8b3672f0da94"*/
//    private static final String SEASON_ID = "1980";
//    private static final String DATE_FROM = "2021-08-12";

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
    private StatsService statsService;
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
    private StatsMapper statsMapper;
    @Autowired
    PasswordEncoder bCryptPasswordEncoder;
    @Autowired
    EPLInfoBot eplInfoBot;

    @Autowired
    public DataInitServiceImpl(UserService userService, TeamService teamService, SeasonService seasonService,
                               WeekService weekService, MatchService matchService, PredictionService predictionService,
                               RoleService roleService, StandingService standingService, NewsService newsService,
                               StatsService statsService, HeadToHeadService headToHeadService) {
        this.userService = userService;
        this.teamService = teamService;
        this.seasonService = seasonService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.roleService = roleService;
        this.standingService = standingService;
        this.newsService = newsService;
        this.statsService = statsService;
        this.headToHeadService = headToHeadService;
    }

    public void allInit() {
        //roleInit();
        //userInit();
        //seasonInit();
        //predictInit();
        //weekInit();
        //matchInit();
        //standingInit();

        //fastStatsInit();
        //roleInit();
        //userInit();
        //teamsInitFromApiFootball();
        //matchInitFromApiFootball();
        currentWeekUpdate();
        //predictInit();
        standingInitFromApiFootball();
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

    private void statsUpdate() {
        List<MatchDto> list = matchService.getAll();
        for (MatchDto matchDto : list) {
            if (matchDto.getLocalDateTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            Long publicId = matchDto.getPublicId();
            Long homeTeamId = matchDto.getHomeTeam().getId();
            Long awayTeamId = matchDto.getAwayTeam().getId();

            StatsDto homeStatsDto = statsService.getByMatchPublicIdAndTeamId(publicId, homeTeamId);
            StatsDto awayStatsDto = statsService.getByMatchPublicIdAndTeamId(publicId, awayTeamId);

            if (homeStatsDto == null || awayStatsDto == null) {
                continue;
            }

            matchDto.setHomeStats(statsMapper.toEntity(homeStatsDto));
            matchDto.setAwayStats(statsMapper.toEntity(awayStatsDto));

            matchService.save(matchDto);
        }
    }

    @SneakyThrows
    private void headToHeadInitFromApiFootball() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/c_18_19.json"))), JsonElement.class);
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

    @SneakyThrows
    private void currentWeekUpdate() {
        HttpResponse<JsonNode> response = Unirest.get("https://v3.football.api-sports.io/fixtures/rounds")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .queryString("league", 39)
                .queryString("season", 2021)
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
                .queryString("season", 2021)
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
                .queryString("season", 2021)
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

                StatsDto homeStats = statsService.getByMatchPublicIdAndTeamId(publicId, homeTeam.getId());
                StatsDto awayStats = statsService.getByMatchPublicIdAndTeamId(publicId, awayTeam.getId());

                if(homeStats == null || awayStats == null) {
                    statsInitFromApiFootball(publicId);
                }

                match = Match.builder()
                        .publicId(publicId)
                        .status(status)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .homeStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(publicId, homeTeam.getId())))
                        .awayStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(publicId, awayTeam.getId())))
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }

    public void fastStatsInit() {
        List<MatchDto> list = matchService.getAll();
        for (MatchDto matchDto : list) {
            if (statsService.getByMatchPublicIdAndTeamId(matchDto.getPublicId(), matchDto.getHomeTeam().getId()) != null || matchDto.getLocalDateTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            statsInitFromApiFootball(matchDto.getPublicId());
            matchDto.setHomeStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(matchDto.getPublicId(), matchDto.getHomeTeam().getId())));
            matchDto.setAwayStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(matchDto.getPublicId(), matchDto.getAwayTeam().getId())));
            matchService.save(matchDto);
        }
    }

    @SneakyThrows
    public void statsInitFromApiFootball(Long publicMatchId) {
        if(!matchService.getByPublicId(publicMatchId).getStatus().equals("ft")) {
            return;
        }
        HttpResponse<JsonNode> resp = Unirest.get("https://v3.football.api-sports.io/fixtures/statistics")
                .header(X_RAPID_API, API_FOOTBALL_TOKEN)
                .queryString("fixture", publicMatchId)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(resp.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();

        JsonArray responses = mainObj.getAsJsonArray("response");
        for (JsonElement response : responses) {
            JsonObject main = response.getAsJsonObject();
            long teamPublicId = main.getAsJsonObject("team").get("id").getAsLong();

            Map<String, Integer> mapStat = new HashMap<>();
            JsonArray statistics = main.getAsJsonArray("statistics");
            for (JsonElement statistic : statistics) {
                JsonObject map = statistic.getAsJsonObject();
                mapStat.put(map.get("type").getAsString(), (map.get("value").isJsonNull()) ?
                        0 : Integer.parseInt(map.get("value").toString().replaceAll("\\D+", "")));
            }
            StatsDto dto = StatsDto.builder()
                    .matchPublicId(publicMatchId)
                    .team(teamMapper.toEntity(teamService.getByPublicId(teamPublicId)))
                    .possessionPercent(mapStat.get("Ball Possession"))
                    .shots(mapStat.get("Total Shots"))
                    .shotsOnTarget(mapStat.get("Shots on Goal"))
                    .shotsOffTarget(mapStat.get("Shots off Goal"))
                    .shotsBlocked(mapStat.get("Blocked Shots"))
                    .insideBoxShots(mapStat.get("Shots insidebox"))
                    .outsideBoxShots(mapStat.get("Shots outsidebox"))
                    .passes(mapStat.get("Total passes"))
                    .passesAccurate(mapStat.get("Passes accurate"))
                    .passPercent(mapStat.get("Passes %"))
                    .corners(mapStat.get("Corner Kicks"))
                    .offsides(mapStat.get("Offsides"))
                    .fouls(mapStat.get("Fouls"))
                    .ballSafe(mapStat.get("Goalkeeper Saves"))
                    .yellowCards(mapStat.get("Yellow Cards"))
                    .redCards(mapStat.get("Red Cards"))
                    .build();

            statsService.save(dto);
        }
    }

    @SneakyThrows
    private void matchInitFromApiFootball() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/21_22.json"))), JsonElement.class);
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

            String round = league.get("round").getAsString();
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(new JsonReader(new FileReader(getFileFromResource("static/json/teams.json"))), JsonElement.class);
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

    /*@SneakyThrows
    private void weekInit() {
        HttpResponse<JsonNode> response = Unirest.get(WEEK)
                .queryString("apikey", TOKEN)
                .queryString("season_id", SEASON_ID)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray data = mainObj.getAsJsonArray("data");

        for (JsonElement games : data) {
            JsonObject weekObj = games.getAsJsonObject();

            week = Week.builder().weekName("week " + weekObj.get("name").getAsString())
                    .isCurrent(!weekObj.get("is_current").isJsonNull())
                    .season(seasonMapper.toEntity(seasonService.getById(1L)))
                    .build();
            weekService.save(weekMapper.toDto(week));
        }
    }*/

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

   /* @SneakyThrows
    private void standingInit() {
        HttpResponse<JsonNode> response = Unirest.get(STANDING)
                .queryString("apikey", TOKEN)
                .queryString("season_id", SEASON_ID)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonObject data = mainObj.getAsJsonObject("data");
        JsonArray standings = data.getAsJsonArray("standings");

        for (JsonElement element : standings) {
            JsonObject standing = element.getAsJsonObject();
            JsonObject overall = standing.getAsJsonObject("overall");
            Standing std = Standing.builder()
                    .team(teamMapper.toEntity(teamService.getByPublicId(standing.get("team_id").getAsLong())))
                    .points(standing.get("points").getAsInt())
                    .result(standing.get("result").toString())
                    .games(overall.get("games_played").getAsInt())
                    .won(overall.get("won").getAsInt())
                    .draw(overall.get("draw").getAsInt())
                    .lost(overall.get("lost").getAsInt())
                    .goalsScored(overall.get("goals_scored").getAsInt())
                    .goalsAgainst(overall.get("goals_against").getAsInt())
                    .build();
            standingService.save(standingMapper.toDto(std));
        }
    }*/

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

   /* @SneakyThrows
    private void seasonInit() {
        HttpResponse<JsonNode> response = Unirest.get(SEASON_INFO + "/" + SEASON_ID)
                .queryString("apikey", TOKEN)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonObject data = mainObj.getAsJsonObject("data");
        String seasonName = data.get("name").getAsString();
        seasonService.saveSeason(SeasonDto.builder().seasonName(seasonName).build());
    }*/

    /*@SneakyThrows
    private void matchUpdate() {

        if (matchService.getAllByTodayDate().isEmpty()) {
            return;
        }

        HttpResponse<JsonNode> response = Unirest.get(DATA_URL)
                .queryString("apikey", TOKEN)
                .queryString("season_id", SEASON_ID)
                .queryString("date_from", LocalDateTime.now().minusDays(1).toLocalDate().toString())
                .queryString("date_to", LocalDateTime.now().plusDays(1).toLocalDate().toString())
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray data = mainObj.getAsJsonArray("data");

        if (data == null) {
            return;
        }

        for (JsonElement games : data) {

            JsonObject matchObj = games.getAsJsonObject();
            id = matchObj.get("match_id").getAsLong();
            JsonObject statsObj = matchObj.getAsJsonObject("stats");


            JsonObject homeTeamObj = matchObj.getAsJsonObject("home_team");
            String homeTeamName = homeTeamObj.get("name").getAsString();
            Team homeTeam = teamMapper.toEntity(teamService.getByName(homeTeamName));

            JsonObject awayTeamObj = matchObj.getAsJsonObject("away_team");
            String awayTeamName = awayTeamObj.get("name").getAsString();
            Team awayTeam = teamMapper.toEntity(teamService.getByName(awayTeamName));

            status = matchObj.get("status").getAsString();
            switch (status) {
                case "halftime" -> status = "ht";
                case "finished" -> status = "ft";
                case "inplay" -> status = "live" + " " + matchObj.get("minute").getAsString() + "'";
                case "postponed" -> {
                    continue;
                }
                default -> status = "-";
            }

            if (statsObj.get("ft_score").isJsonNull()) {

                //create match if it not starts yet
                match = Match.builder()
                        .publicId(id)
                        .status(status)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .build();
            } else {

                //create match if it starts
                homeTeamScore = statsObj.get("home_score").getAsInt();
                awayTeamScore = statsObj.get("away_score").getAsInt();

                result = (homeTeamScore.equals(awayTeamScore)) ? "D" : (homeTeamScore > awayTeamScore) ? "H" : "A";

                statsInit(id);

                match = Match.builder()
                        .publicId(id)
                        .status(status)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .homeStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(id, homeTeam.getId())))
                        .awayStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(id, awayTeam.getId())))
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }*/

   /* @SneakyThrows
    private void matchInit() {
        HttpResponse<JsonNode> response = Unirest.get(DATA_URL)
                .queryString("apikey", TOKEN)
                .queryString("season_id", SEASON_ID)
                .queryString("date_from", DATE_FROM)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray data = mainObj.getAsJsonArray("data");

        for (JsonElement games : data) {

            //get match info
            JsonObject matchObj = games.getAsJsonObject();
            id = matchObj.get("match_id").getAsLong();
            status = matchObj.get("status").getAsString();
            switch (status) {
                case "halftime" -> status = "ht";
                case "finished" -> status = "ft";
                case "inplay" -> status = "live" + " " + matchObj.get("minute").getAsString() + "'";
                case "postponed" -> {
                    continue;
                }
                default -> status = "-";
            }

            //get match date and time
            matchDateTime = df.parse(matchObj.get("match_start_iso").getAsString()).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime().plusHours(3);

            //get week info
            JsonObject weekObj = matchObj.getAsJsonObject("round");
            week = Week.builder().weekName("week " + weekObj.get("name").getAsString())
                    .isCurrent(!weekObj.get("is_current").isJsonNull())
                    .season(seasonMapper.toEntity(seasonService.getById(1L)))
                    .build();
            WeekDto weekDto = weekService.save(weekMapper.toDto(week));

            // get playing teams info
            JsonObject homeTeamObj = matchObj.getAsJsonObject("home_team");
            homeTeam = Team.builder().teamName(homeTeamObj.get("name").getAsString())
                    .publicId(homeTeamObj.get("team_id").getAsLong())
                    .code(homeTeamObj.get("short_code").getAsString())
                    .logo(homeTeamObj.get("logo").getAsString())
                    .build();
            if (homeTeam.getCode().equals("TOT")) {
                homeTeam.setPublicId(12295L);
            } else if (homeTeam.getCode().equals("SOT")) {
                homeTeam.setPublicId(12423L);
                homeTeam.setLogo("https://cdn.sportdataapi.com/images/soccer/teams/100/8.png");
            }
            TeamDto homeTeamDto = teamService.saveTeam(teamMapper.toDto(homeTeam));

            JsonObject awayTeamObj = matchObj.getAsJsonObject("away_team");
            awayTeam = Team.builder().teamName(awayTeamObj.get("name").getAsString())
                    .publicId(awayTeamObj.get("team_id").getAsLong())
                    .code(awayTeamObj.get("short_code").getAsString())
                    .logo(awayTeamObj.get("logo").getAsString())
                    .build();
            if (awayTeam.getCode().equals("TOT")) {
                awayTeam.setPublicId(12295L);
            } else if (awayTeam.getCode().equals("SOT")) {
                awayTeam.setPublicId(12423L);
                awayTeam.setLogo("https://cdn.sportdataapi.com/images/soccer/teams/100/8.png");
            }
            TeamDto awayTeamDto = teamService.saveTeam(teamMapper.toDto(awayTeam));

            //get match stats
            JsonObject statsObj = matchObj.getAsJsonObject("stats");

            if (statsObj.get("ft_score").isJsonNull()) {

                //create match if it not starts yet
                match = Match.builder()
                        .publicId(id)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(teamMapper.toEntity(homeTeamDto))
                        .awayTeam(teamMapper.toEntity(awayTeamDto))
                        .build();
            } else {

                //create match if it starts
                homeTeamScore = statsObj.get("home_score").getAsInt();
                awayTeamScore = statsObj.get("away_score").getAsInt();

                result = (homeTeamScore.equals(awayTeamScore)) ? "D" : (homeTeamScore > awayTeamScore) ? "H" : "A";

                //statsInit(id);

                match = Match.builder()
                        .publicId(id)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(teamMapper.toEntity(homeTeamDto))
                        .awayTeam(teamMapper.toEntity(awayTeamDto))
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .homeStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(id, homeTeam.getId())))
                        .awayStats(statsMapper.toEntity(statsService.getByMatchPublicIdAndTeamId(id, awayTeam.getId())))
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }*/

    /*@SneakyThrows
    public void statsInit(Long publicMatchId) {

        HttpResponse<JsonNode> response = Unirest.get(STATS + "/" + publicMatchId)
                .queryString("apikey", TOKEN)
                .asJson();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Create data array from api
        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonObject dataObj = mainObj.getAsJsonObject("data");

        JsonArray matchStats = dataObj.getAsJsonArray("match_statistics");

        if (matchStats == null) {
            return;
        }

        for (JsonElement matchStat : matchStats) {
            JsonObject statsObj = matchStat.getAsJsonObject();

            long teamPublicId = switch (statsObj.get("team_id").getAsInt()) {
                case 12430 -> 12295L;
                case 2959 -> 12423L;
                default -> statsObj.get("team_id").getAsLong();
            };

            StatsDto dto = StatsDto.builder()
                    .matchPublicId(dataObj.get("match_id").getAsLong())
                    .team(teamMapper.toEntity(teamService.getByPublicId(teamPublicId)))
                    .possessionPercent(statsObj.get("possessionpercent").getAsInt())
                    .shots(statsObj.get("shots_total").getAsInt())
                    .shotsOnTarget(statsObj.get("shots_on_target").getAsInt())
                    .shotsOffTarget(statsObj.get("shots_off_target").getAsInt())
                    .shotsBlocked(statsObj.get("shots_blocked").getAsInt())
                    .corners(statsObj.get("corners").getAsInt())
                    .offsides(statsObj.get("offsides").getAsInt())
                    .freeKick(statsObj.get("free_kick").getAsInt())
                    .fouls(statsObj.get("fouls").getAsInt())
                    .throwIn(statsObj.get("throw_in").getAsInt())
                    .goalKick(statsObj.get("goal_kick").getAsInt())
                    .ballSafe(statsObj.get("ball_safe").getAsInt())
                    .yellowCards(statsObj.get("yellowcards").getAsInt())
                    .yellowRedCards(statsObj.get("yellowredcards").getAsInt())
                    .redCards(statsObj.get("redcards").getAsInt())
                    .build();

            statsService.save(dto);
        }
    }*/

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
