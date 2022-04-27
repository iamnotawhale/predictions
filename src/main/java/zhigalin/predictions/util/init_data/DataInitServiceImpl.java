package zhigalin.predictions.util.init_data;

import com.google.gson.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.converter.event.StatsMapper;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.dto.event.StatsDto;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.StatsService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.RoleService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.model.EPLInfoBot;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;


@Service
@NoArgsConstructor
public class DataInitServiceImpl {

    private static final String SEASON_INFO = "https://app.sportdataapi.com/api/v1/soccer/seasons";
    private static final String DATA_URL = "https://app.sportdataapi.com/api/v1/soccer/matches";
    private static final String STANDING = "https://app.sportdataapi.com/api/v1/soccer/standings";
    private static final String STATS = "https://app.sportdataapi.com/api/v1/soccer/matches";
    private static final String WEEK = "https://app.sportdataapi.com/api/v1/soccer/rounds";
    private static final String TOKEN = /*"577b6040-b8e8-11ec-804e-414c550f7861";*/ "8002bee0-9cd1-11ec-9d81-8b3672f0da94";
    private static final String SEASON_ID = "1980";
    private static final String DATE_FROM = "2021-08-12";
    private static final String BOT_TOKEN = "5370831950:AAHS3TjDNMpyfC9HSNeM91LROZkmVSWGJRQ";
    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH); //"2021-08-13T19:00:00+00:00"

    private Long id;
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
                               RoleService roleService, StandingService standingService, NewsService newsService, StatsService statsService) {
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
    }

    public void allInit() {
        //roleInit();
        //userInit();
        //seasonInit();
        //predictInit();
        //weekInit();
        //matchInit();
        //standingInit();
        //sendBotMessage();
        Thread run = new Thread(() -> {
            while (true) {
                try {
                    matchUpdate();
                    newsInit();
                    Thread.sleep(180000); //1000 - 1 сек
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        run.start();
    }

    @SneakyThrows
    private void sendBotMessage() {
        List<Match> matchList = matchService.getAllByTodayDate().stream().map(matchMapper::toEntity).toList();
        if (!matchList.isEmpty()) {

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(eplInfoBot);

            for (Match match : matchList) {
                Chat chat = new Chat();
                chat.setId(-554246142L);

                Message message = new Message();
                message.setChat(chat);
                if (!match.getStatus().equals("-")) {
                    message.setText(match.getHomeTeam().getTeamName() + " " + match.getHomeTeamScore() + " " + match.getStatus()+ " " + match.getAwayTeamScore() + " "  + match.getAwayTeam().getTeamName());
                } else {
                    message.setText(match.getHomeTeam().getTeamName() + " " + match.getMatchTime() + " " + match.getAwayTeam().getTeamName());
                }

                Update update = new Update();
                update.setMessage(message);

                eplInfoBot.onUpdateReceived(update);
            }
        }
    }

    @SneakyThrows
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
    }

    @SneakyThrows
    private void newsInit() {
        String title;
        String link;
        LocalDateTime dateTime;
        DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);

        URL feedSource = new URL("https://www.sports.ru/stat/export/rss/taglenta.xml?id=1363805");
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(feedSource));
        List<SyndEntry> res = feed.getEntries();

        for (Object re : res) {
            link = ((SyndEntryImpl) re).getLink();
            title = ((SyndEntryImpl) re).getTitle();
            dateTime = formatter.parse(((SyndEntryImpl) re).getPublishedDate().toString())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            newsService.save(NewsDto.builder().title(title).link(link).dateTime(dateTime).build());
        }
    }

    @SneakyThrows
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
    }

    private void roleInit() {
        admin = Role.builder().role("ADMIN").build();
        user = Role.builder().role("USER").build();
        roleService.save(roleMapper.toDto(admin));
        roleService.save(roleMapper.toDto(user));
    }

    private void predictInit() {
        for (int i = 0; i < 100; i++) {
            Long matchId = (long) new Random().nextInt(370);
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

    @SneakyThrows
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
    }

    @SneakyThrows
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
    }

    @SneakyThrows
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
    }

    @SneakyThrows
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
    }
}
