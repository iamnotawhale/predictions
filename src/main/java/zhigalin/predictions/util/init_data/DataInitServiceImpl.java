package zhigalin.predictions.util.init_data;

import com.google.gson.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.RoleService;
import zhigalin.predictions.service.user.UserService;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;


@Service
@NoArgsConstructor
public class DataInitServiceImpl {

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH); //"2021-08-13T19:00:00+00:00"
    private final String SEASON_INFO = "https://app.sportdataapi.com/api/v1/soccer/seasons/";
    private final String DATA_URL = "https://app.sportdataapi.com/api/v1/soccer/matches";
    private final String STANDING = "https://app.sportdataapi.com/api/v1/soccer/standings";
    private final String TOKEN = "8002bee0-9cd1-11ec-9d81-8b3672f0da94";
    private final String SEASON_ID = "1980";
    private final String DATE_FROM = "2021-08-12";

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
    private PredictionMapper predictionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    PasswordEncoder bCryptPasswordEncoder;

    @Autowired
    public DataInitServiceImpl(UserService userService, TeamService teamService, SeasonService seasonService,
                               WeekService weekService, MatchService matchService, PredictionService predictionService,
                               RoleService roleService, StandingService standingService) {
        this.userService = userService;
        this.teamService = teamService;
        this.seasonService = seasonService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.roleService = roleService;
        this.standingService = standingService;
    }

    public void allInit() {
        /*Thread run = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        roleInit();
                        userInit();
                        seasonInit();
                        matchInit();
                        predictInit();
                        standingInit();
                        Thread.sleep(300000); //1000 - 1 сек
                    } catch (InterruptedException ex) {
                    }
                }
            }
        });
        run.start(); // заводим*/
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
        for (int i = 1; i < 5; i++) {
            Integer htScore = new Random().nextInt(4);
            Integer atScore = new Random().nextInt(4);
            MatchDto dto = matchService.getById(1L);
            predictionService.save(PredictionDto.builder()
                    .match(matchMapper.toEntity(dto))
                    .awayTeamScore(atScore)
                    .homeTeamScore(htScore)
                    .user(userMapper.toEntity(userService.getById((long) i)))
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
                case "halftime":
                    break;
                case "inplay":
                    status = status + " " + matchObj.get("minute").getAsString() + "'";
                    break;
                case "postponed":
                    continue;
                default:
                    status = "-";
                    break;
            }

            //get match date and time
            matchDateTime = df.parse(matchObj.get("match_start_iso").getAsString()).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime().plusHours(3);

            //get week info
            JsonObject weekObj = matchObj.getAsJsonObject("round");
            week = Week.builder().weekName("week " + weekObj.get("name").getAsString())
                    .isCurrent(!weekObj.get("is_current").isJsonNull())
                    .season(seasonMapper.toEntity(seasonService.getById(1l)))
                    .build();
            WeekDto weekDto = weekService.save(weekMapper.toDto(week));

            // get playing teams info
            JsonObject homeTeamObj = matchObj.getAsJsonObject("home_team");
            homeTeam = Team.builder().teamName(homeTeamObj.get("name").getAsString())
                    .publicId(homeTeamObj.get("team_id").getAsLong())
                    .code(homeTeamObj.get("short_code").getAsString())
                    .logo(homeTeamObj.get("logo").getAsString())
                    .build();
            if(homeTeam.getCode().equals("TOT")) {
                homeTeam.setPublicId(12295L);
            } else if(homeTeam.getCode().equals("SOT")) {
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
            if(awayTeam.getCode().equals("TOT")) {
                awayTeam.setPublicId(12295L);
            } else if(awayTeam.getCode().equals("SOT")) {
                awayTeam.setPublicId(12423L);
                awayTeam.setLogo("https://cdn.sportdataapi.com/images/soccer/teams/100/8.png");
            }
            TeamDto awayTeamDto = teamService.saveTeam(teamMapper.toDto(awayTeam));

            //get match stats
            JsonObject statsObj = matchObj.getAsJsonObject("stats");
            if (statsObj.get("ft_score").isJsonNull()) {

                //create match if it not starts yet
                match = Match.builder()
                        .id(id)
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

                if (homeTeamScore > awayTeamScore) {
                    result = "H";
                } else if (homeTeamScore < awayTeamScore) {
                    result = "A";
                } else {
                    result = "D";
                }

                match = Match.builder()
                        .id(id)
                        .status(status)
                        .localDateTime(matchDateTime)
                        .matchDate(matchDateTime.toLocalDate())
                        .matchTime(matchDateTime.toLocalTime())
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(teamMapper.toEntity(homeTeamDto))
                        .awayTeam(teamMapper.toEntity(awayTeamDto))
                        .homeTeamScore(homeTeamScore)
                        .awayTeamScore(awayTeamScore)
                        .result(result)
                        .build();
            }
            matchService.save(matchMapper.toDto(match));
        }
    }
}
