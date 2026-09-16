package zhigalin.predictions.service.event;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.UserWeekBonusMatchDao;
import zhigalin.predictions.service.user.UserService;

@Service
public class UserWeekBonusMatchService {

    /** Personal week-bonus scoring starts from this EPL week inclusive. */
    public static final int MIN_WEEK_ID = 5;

    private final UserWeekBonusMatchDao dao;
    private final MatchService matchService;
    private final UserService userService;

    public UserWeekBonusMatchService(
            UserWeekBonusMatchDao dao,
            MatchService matchService,
            UserService userService
    ) {
        this.dao = dao;
        this.matchService = matchService;
        this.userService = userService;
    }

    public static boolean isEligibleWeek(int weekId) {
        return weekId >= MIN_WEEK_ID;
    }

    public Optional<Integer> findAssigned(int userId, int weekId) {
        if (!isEligibleWeek(weekId)) {
            return Optional.empty();
        }
        return dao.findMatchPublicId(userId, weekId);
    }

    public boolean isWeekBonusMatch(int userId, int weekId, int matchPublicId) {
        if (!isEligibleWeek(weekId)) {
            return false;
        }
        return dao.findMatchPublicId(userId, weekId)
                .map(id -> id == matchPublicId)
                .orElse(false);
    }

    /**
     * Ensure every user has a random unfinished (or any) match for the week.
     * Stable once assigned. No-op before {@link #MIN_WEEK_ID}.
     */
    public void ensureAssignedForWeek(int weekId) {
        if (!isEligibleWeek(weekId)) {
            dao.deleteBeforeWeek(MIN_WEEK_ID);
            return;
        }
        dao.deleteBeforeWeek(MIN_WEEK_ID);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        if (matches == null || matches.isEmpty()) {
            return;
        }
        List<Match> pool = matches.stream()
                .filter(m -> m.getStatus() == null
                             || "ns".equalsIgnoreCase(m.getStatus())
                             || "pst".equalsIgnoreCase(m.getStatus()))
                .toList();
        if (pool.isEmpty()) {
            pool = matches;
        }
        for (User user : userService.findAll()) {
            if (dao.findMatchPublicId(user.getId(), weekId).isPresent()) {
                continue;
            }
            Match pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            dao.insert(user.getId(), weekId, pick.getPublicId());
        }
    }

    public Integer ensureAssignedForUser(int userId, int weekId) {
        if (!isEligibleWeek(weekId)) {
            return null;
        }
        Optional<Integer> existing = dao.findMatchPublicId(userId, weekId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ensureAssignedForWeek(weekId);
        return dao.findMatchPublicId(userId, weekId).orElse(null);
    }
}
