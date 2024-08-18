package zhigalin.predictions.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;


import jakarta.annotation.PostConstruct;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.user.UserService;

@Service
public class DaoUtil {

    private final MatchService matchService;
    private final TeamService teamService;
    public final static Map<Integer, Team> TEAMS = new HashMap<>();
    public final static Map<Integer, User> USERS = new HashMap<>();
    private final UserService userService;

    public DaoUtil(MatchService matchService, TeamService teamService, UserService userService) {
        this.matchService = matchService;
        this.teamService = teamService;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        teamService.findAll().forEach(team -> {
            TEAMS.put(team.getPublicId(), team);
        });
        userService.findAll().forEach(user -> {
            USERS.put(user.getId(), user);
        });
    }

    public static <T> T getNullableResult(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

}
