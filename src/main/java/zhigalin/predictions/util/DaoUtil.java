package zhigalin.predictions.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.user.UserService;

@Service
public class DaoUtil {

    public static int currentWeekId;

    private final TeamService teamService;
    private final UserService userService;
    private final WeekService weekService;

    public final static Map<Integer, Team> TEAMS = new HashMap<>();
    public final static Map<Integer, User> USERS = new HashMap<>();

    public DaoUtil(TeamService teamService, UserService userService, WeekService weekService) {
        this.teamService = teamService;
        this.userService = userService;
        this.weekService = weekService;
    }

    @PostConstruct
    public void init() {
        teamService.findAll().forEach(team -> {
            TEAMS.put(team.getPublicId(), team);
        });
        userService.findAll().forEach(user -> {
            USERS.put(user.getId(), user);
        });
        currentWeekId = weekService.findCurrentWeek().getId();
    }

    @Scheduled(cron = "0 0 0 * * *")
    private void currentWeekUpdate() {
        currentWeekId = weekService.findCurrentWeek().getId();
    }

    public static <T> T getNullableResult(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

}
