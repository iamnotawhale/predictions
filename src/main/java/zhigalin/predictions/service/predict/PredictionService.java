package zhigalin.predictions.service.predict;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<MatchPrediction> findAllByUserId(int userId) {
        return predictionDao.findAllByUserId(userId);
    }

    public List<MatchPrediction> findAllByUserIdAndWeekId(int userId, int weekId) {
        return findAllByWeekId(weekId).stream().filter(prediction -> prediction.prediction().getUserId() == userId).toList();
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
        int points;
        Integer realHomeScore = match.getHomeTeamScore();
        Integer realAwayScore = match.getAwayTeamScore();
        Integer predictHomeScore = prediction.getHomeTeamScore();
        Integer predictAwayScore = prediction.getAwayTeamScore();
        if (predictHomeScore == null || predictAwayScore == null) {
            points = -1;
        } else {
            points = realHomeScore == null || realAwayScore == null ? 0
                    : realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore) ? 4
                    : realHomeScore - realAwayScore == predictHomeScore - predictAwayScore ? 2
                    : realHomeScore > realAwayScore && predictHomeScore > predictAwayScore ? 1
                    : realHomeScore < realAwayScore && predictHomeScore < predictAwayScore ? 1 : -1;
        }
        predictionDao.updatePoints(matchId, userId, points);
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

    public List<Prediction> getAllByMatches(List<Match> matches) {
        return predictionDao.getAllByMatches(matches);
    }

    private void updatePredictions(Match match, List<User> users) {
        for (User user : users) {
            updatePoints(match.getPublicId(), user.getId());
        }
    }

    public void updateUnpredictable() {
        List<Match> allMatches = matchService.findAll().stream()
                .filter(match -> !match.getStatus().equals("pst") && match.getLocalDateTime().isBefore(LocalDateTime.now()))
                .toList();
        List<User> users = userService.findAll();

        getAllByMatches(allMatches).stream()
                .collect(Collectors.groupingBy(Prediction::getMatchPublicId))
                .forEach((matchPublicId, predictions) -> {
                            if (!predictions.isEmpty()) {
                                List<User> usersWithNoPredicts = users.stream()
                                        .filter(user -> !predictions.stream()
                                                .map(Prediction::getUserId)
                                                .toList()
                                                .contains(user.getId()))
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

    public void updateByMatch(Match match) {
        List<User> users = userService.findAll();
        if (match != null) {
            List<Prediction> predictions = getByMatchPublicId(match.getPublicId());
            if (!predictions.isEmpty() && predictions.size() < 4) {
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictions.stream()
                                .map(Prediction::getUserId)
                                .toList()
                                .contains(user.getId()))
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

    public List<MatchPrediction> getPredictionsByUserAndWeek(int userId, int weekId) {
        return predictionDao.getPredictionsByUserAndWeek(userId, weekId);
    }

    public List<MatchPrediction> getAllWeeklyPredictionsByUserTelegramId(int weekId, String telegramId) {
        return predictionDao.findAllByWeekIdAndUserTelegramId(weekId, telegramId);
    }

    public List<Integer> getPredictableWeeksByUserTelegramId(String telegramId) {
        return predictionDao.findPredictableWeeksByUserTelegramId(telegramId);
    }
}
