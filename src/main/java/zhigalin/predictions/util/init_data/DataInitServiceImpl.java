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
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.SeasonService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.RoleService;
import zhigalin.predictions.service.user.UserService;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


@Service
@NoArgsConstructor
public class DataInitServiceImpl {

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH); //"2021-08-13T19:00:00+00:00"
    private final String DATA_URL = "https://app.sportdataapi.com/api/v1/soccer/matches";
    private final String TOKEN = "8002bee0-9cd1-11ec-9d81-8b3672f0da94";
    private final String SEASON_ID = "1980";
    private final String DATE_FROM = "2021-08-12";

    private Long id;
    private Week week;
    private LocalDateTime matchDate;
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

    @Autowired
    private SeasonMapper seasonMapper;

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
                               RoleService roleService) {
        this.userService = userService;
        this.teamService = teamService;
        this.seasonService = seasonService;
        this.weekService = weekService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.roleService = roleService;
    }

    public void allInit() {
        roleInit();
        userInit();
        seasonInit();
        matchInit();
        predictInit();
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
        List<UserDto> users = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            Role role = roleMapper.toEntity(roleService.findById((long) 1));
            UserDto userDto = UserDto.builder().login("user" + i).password(bCryptPasswordEncoder.encode("user" + i))
                    .roles(Collections.singleton(role)).build();
            users.add(userDto);
        }
        userService.saveAll(users);
    }

    private void seasonInit() {
        String seasonName = "2021-22";
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

        JsonObject mainObj = gson.fromJson(response.getBody().getObject().toString(), JsonElement.class).getAsJsonObject();
        JsonArray data = mainObj.getAsJsonArray("data");

        for (JsonElement games : data) {
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

            matchDate = df.parse(matchObj.get("match_start_iso").getAsString()).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime().plusHours(3);

            JsonObject weekObj = matchObj.getAsJsonObject("round");
            week = Week.builder().weekName("week " + weekObj.get("name").getAsString())
                    .isCurrent(!weekObj.get("is_current").isJsonNull())
                    .season(seasonMapper.toEntity(seasonService.getById(1l)))
                    .build();
            WeekDto weekDto = weekService.save(weekMapper.toDto(week));

            JsonObject homeTeamObj = matchObj.getAsJsonObject("home_team");
            homeTeam = Team.builder().teamName(homeTeamObj.get("name").getAsString())
                    .code(homeTeamObj.get("short_code").getAsString().toLowerCase())
                    .logo(homeTeamObj.get("logo").getAsString())
                    .build();
            TeamDto homeTeamDto = teamService.saveTeam(teamMapper.toDto(homeTeam));
            JsonObject awayTeamObj = matchObj.getAsJsonObject("away_team");
            awayTeam = Team.builder().teamName(awayTeamObj.get("name").getAsString())
                    .code(awayTeamObj.get("short_code").getAsString().toLowerCase())
                    .logo(awayTeamObj.get("logo").getAsString())
                    .build();
            TeamDto awayTeamDto = teamService.saveTeam(teamMapper.toDto(awayTeam));


            JsonObject statsObj = matchObj.getAsJsonObject("stats");
            if (statsObj.get("ft_score").isJsonNull()) {
                match = Match.builder()
                        .id(id)
                        .status(status)
                        .matchDate(matchDate)
                        .week(weekMapper.toEntity(weekDto))
                        .homeTeam(teamMapper.toEntity(homeTeamDto))
                        .awayTeam(teamMapper.toEntity(awayTeamDto))
                        .build();
            } else {
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
                        .matchDate(matchDate)
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
