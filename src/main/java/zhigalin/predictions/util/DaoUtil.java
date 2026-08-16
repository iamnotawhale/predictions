package zhigalin.predictions.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.user.UserService;

@Service
public class DaoUtil {

    private static final Logger log = LoggerFactory.getLogger("server");

    public static int currentWeekId;

    private static TeamService teamServiceRef;

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

    @EventListener(ApplicationStartedEvent.class)
    public void init() {
        teamServiceRef = teamService;
        reloadTeams();
        userService.findAll().forEach(user -> USERS.put(user.getId(), user));
        Week currentWeek = weekService.findCurrentWeek();
        if (currentWeek != null) {
            currentWeekId = currentWeek.getId();
        } else {
            currentWeekId = 1;
        }
        log.info("DaoUtil cache ready: {} teams, {} users, week {}", TEAMS.size(), USERS.size(), currentWeekId);
    }

    public static Team team(int publicId) {
        Team cached = TEAMS.get(publicId);
        if (cached != null) {
            return cached;
        }
        if (teamServiceRef == null) {
            return null;
        }
        Team loaded = teamServiceRef.findByPublicId(publicId);
        if (loaded != null) {
            TEAMS.put(publicId, loaded);
        }
        return loaded;
    }

    public static void reloadTeams() {
        TEAMS.clear();
        if (teamServiceRef != null) {
            teamServiceRef.findAll().forEach(team -> TEAMS.put(team.getPublicId(), team));
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    private void currentWeekUpdate() {
        Week currentWeek = weekService.findCurrentWeek();
        if (currentWeek == null) {
            log.warn("currentWeekUpdate: no current week in DB, keeping weekId={}", currentWeekId);
            return;
        }
        currentWeekId = currentWeek.getId();
    }

    public static <T> T getNullableResult(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

}
