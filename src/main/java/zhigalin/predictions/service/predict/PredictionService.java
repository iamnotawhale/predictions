package zhigalin.predictions.service.predict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.user.UserService;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final PredictionDao predictionDao;
    private final MatchService matchService;
    private final UserService userService;

    public PredictionService(PredictionDao predictionDao, MatchService matchService, UserService userService) {
        this.predictionDao = predictionDao;
        this.matchService = matchService;
        this.userService = userService;
    }

    public void save(Prediction prediction) {
        predictionDao.save(prediction);
    }

    public void save(String telegramId, String homeTeam, String awayTeam, int homeScore, int awayScore) {
        predictionDao.save(telegramId, homeTeam, awayTeam, homeScore, awayScore);
    }

    public Prediction findByMatchIdAndUserId(int matchId, int userId) {
        return predictionDao.findByMatchIdAndUserId(matchId, userId);
    }

    public List<MatchPrediction> findAllByWeekId(int weekId) {
        return predictionDao.findAllByWeekId(weekId);
    }

    public void deleteById(int userId, int matchPublicId) {
        predictionDao.delete(userId, matchPublicId);
    }

    public void deleteByUserTelegramIdAndTeams(String telegramId, String homeTeam, String awayTeam) {
        predictionDao.deleteByUserTelegramIdAndTeams(telegramId, homeTeam, awayTeam);
    }

    public void updatePoints(int matchId, int userId) {
        Prediction prediction = predictionDao.findByMatchIdAndUserId(matchId, userId);
        if (prediction == null) {
            return;
        }
        Match match = matchService.findByPublicId(prediction.getMatchPublicId());
        int points = computePoints(
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                prediction.getHomeTeamScore(),
                prediction.getAwayTeamScore()
        );
        predictionDao.updatePoints(matchId, userId, points);
    }

    /**
     * Same scoring rules as persisted points: exact=4, goal diff=2, outcome=1, else=-1, incomplete=0/-1.
     */
    public static int computePoints(Integer realHomeScore, Integer realAwayScore,
                                    Integer predictHomeScore, Integer predictAwayScore) {
        if (predictHomeScore == null || predictAwayScore == null) {
            return -1;
        }
        if (realHomeScore == null || realAwayScore == null) {
            return 0;
        }
        if (realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore)) {
            return 4;
        }
        if (realHomeScore - realAwayScore == predictHomeScore - predictAwayScore) {
            return 2;
        }
        if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
            return 1;
        }
        if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
            return 1;
        }
        return -1;
    }

    public boolean isExist(int userId, int matchId) {
        return predictionDao.isExist(userId, matchId);
    }

    public boolean isExist(String userTelegramId, int matchId) {
        return predictionDao.isExist(userTelegramId, matchId);
    }

    public Prediction getByUserTelegramIdAndTeams(String telegramId, String homeTeam, String awayTeam) {
        return predictionDao.getByUserTelegramIdAndTeams(telegramId, homeTeam, awayTeam);
    }

    public List<Prediction> getByMatchPublicId(int publicId) {
        return predictionDao.findAllByMatchIds(List.of(publicId));
    }

    public Map<Integer, Prediction> predictionsByMatchForUser(String telegramId, Collection<Integer> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = matchIds instanceof List<Integer> list ? list : List.copyOf(matchIds);
        return predictionDao.findAllByMatchIdsAndTelegramId(ids, telegramId).stream()
                .collect(Collectors.toMap(Prediction::getMatchPublicId, p -> p, (a, b) -> a));
    }

    public List<Prediction> getAllByMatches(List<Match> matches) {
        return predictionDao.getAllByMatches(matches);
    }

    private void updatePredictions(Match match, List<User> users) {
        List<Prediction> predictions = getByMatchPublicId(match.getPublicId());
        Map<Integer, Prediction> byUser = predictions.stream()
                .collect(Collectors.toMap(Prediction::getUserId, p -> p, (a, b) -> a));
        List<PredictionDao.PointsUpdate> updates = new ArrayList<>();
        for (User user : users) {
            Prediction prediction = byUser.get(user.getId());
            int points = computePoints(
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    prediction != null ? prediction.getHomeTeamScore() : null,
                    prediction != null ? prediction.getAwayTeamScore() : null
            );
            updates.add(new PredictionDao.PointsUpdate(match.getPublicId(), user.getId(), points));
        }
        predictionDao.updatePointsBatch(updates);
    }

    public void updateUnpredictable() {
        List<Match> allMatches = matchService.findPastNonPostponedMatches();
        List<User> users = userService.findAll();

        getAllByMatches(allMatches).stream()
                .collect(Collectors.groupingBy(Prediction::getMatchPublicId))
                .forEach((matchPublicId, predictions) -> {
                            if (!predictions.isEmpty()) {
                                Set<Integer> predictedUserIds = predictions.stream()
                                        .map(Prediction::getUserId)
                                        .collect(Collectors.toCollection(HashSet::new));
                                List<User> usersWithNoPredicts = users.stream()
                                        .filter(user -> !predictedUserIds.contains(user.getId()))
                                        .toList();
                                for (User user : usersWithNoPredicts) {
                                    save(
                                            Prediction.builder()
                                                    .matchPublicId(matchPublicId)
                                                    .points(-1)
                                                    .homeTeamScore(null)
                                                    .awayTeamScore(null)
                                                    .userId(user.getId())
                                                    .build()
                                    );
                                }
                            }
                        }
                );
    }

    public void recalculateFinishedMatchPoints() {
        var finished = matchService.findFinishedMatches();
        for (Match match : finished) {
            updateByMatch(match);
        }
        log.info("Recalculated points for {} finished matches", finished.size());
    }

    public void updateByMatch(Match match) {
        List<User> users = userService.findAll();
        if (match != null) {
            List<Prediction> predictions = getByMatchPublicId(match.getPublicId());
            if (!predictions.isEmpty() && predictions.size() < 4) {
                Set<Integer> predictedUserIds = predictions.stream()
                        .map(Prediction::getUserId)
                        .collect(Collectors.toCollection(HashSet::new));
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictedUserIds.contains(user.getId()))
                        .toList();
                for (User user : usersWithNoPredicts) {
                    save(Prediction.builder()
                            .matchPublicId(match.getPublicId())
                            .points(-1)
                            .homeTeamScore(null)
                            .awayTeamScore(null)
                            .userId(user.getId())
                            .build());
                }
            }
            updatePredictions(match, users);
        }
    }

    public Map<String, Integer> getAllPointsByUsers() {
        return predictionDao.getAllPointsByUsers().stream()
                .sorted(Comparator.comparingInt(Points::getValue).reversed())
                .collect(Collectors.toMap(Points::getLogin, Points::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Map<String, Integer> getWeeklyUsersPoints(int weekId) {
        return predictionDao.getAllPointsByWeekId(weekId).stream()
                .sorted(Comparator.comparingInt(Points::getValue).reversed())
                .collect(Collectors.toMap(Points::getLogin, Points::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public List<MatchPrediction> getAllWeeklyPredictionsByUserTelegramId(int weekId, String telegramId) {
        return predictionDao.findAllByWeekIdAndUserTelegramId(weekId, telegramId);
    }

    public List<Integer> getPredictableWeeksByUserTelegramId(String telegramId) {
        return predictionDao.findPredictableWeeksByUserTelegramId(telegramId);
    }

    public Map<String, Map<Integer, Integer>> getAllUsersCumulativePoints() {
        return predictionDao.getAllUsersCumulativePoints();
    }
}
