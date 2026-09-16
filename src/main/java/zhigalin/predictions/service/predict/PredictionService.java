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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.BonusMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.user.UserService;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final PredictionDao predictionDao;
    private final MatchService matchService;
    private final UserService userService;
    private final FlooredPointsService flooredPointsService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final BonusMatchDao bonusMatchDao;

    public PredictionService(
            PredictionDao predictionDao,
            MatchService matchService,
            UserService userService,
            FlooredPointsService flooredPointsService,
            @Lazy UserWeekBonusMatchService userWeekBonusMatchService,
            BonusMatchDao bonusMatchDao
    ) {
        this.predictionDao = predictionDao;
        this.matchService = matchService;
        this.userService = userService;
        this.flooredPointsService = flooredPointsService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.bonusMatchDao = bonusMatchDao;
    }

    public void save(Prediction prediction) {
        predictionDao.save(prediction);
    }

    public void save(String telegramId, String homeTeam, String awayTeam, int homeScore, int awayScore) {
        predictionDao.save(telegramId, homeTeam, awayTeam, homeScore, awayScore);
    }

    public void saveBonus(String telegramId, int bonusMatchId, int homeScore, int awayScore) {
        predictionDao.saveBonus(telegramId, bonusMatchId, homeScore, awayScore);
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

    public void deleteBonusByUserTelegramId(String telegramId, int bonusMatchId) {
        predictionDao.deleteBonusByUserTelegramId(telegramId, bonusMatchId);
    }

    public void updatePoints(int matchId, int userId) {
        Prediction prediction = predictionDao.findByMatchIdAndUserId(matchId, userId);
        if (prediction == null) {
            return;
        }
        Match match = matchService.findByPublicId(prediction.getMatchPublicId());
        ScoringMode mode = scoringModeForEpl(userId, match);
        int points = computePoints(
                mode,
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                prediction.getHomeTeamScore(),
                prediction.getAwayTeamScore()
        );
        predictionDao.updatePoints(matchId, userId, points);
    }

    /** Backward-compatible EPL scoring. */
    public static int computePoints(Integer realHomeScore, Integer realAwayScore,
                                    Integer predictHomeScore, Integer predictAwayScore) {
        return computePoints(ScoringMode.EPL, realHomeScore, realAwayScore, predictHomeScore, predictAwayScore);
    }

    public static int computePoints(
            ScoringMode mode,
            Integer realHomeScore,
            Integer realAwayScore,
            Integer predictHomeScore,
            Integer predictAwayScore
    ) {
        ScoringMode m = mode != null ? mode : ScoringMode.EPL;
        if (predictHomeScore == null || predictAwayScore == null) {
            return switch (m) {
                case EPL, EPL_WEEK_BONUS -> -1;
                case CUP -> 0;
            };
        }
        if (realHomeScore == null || realAwayScore == null) {
            return 0;
        }
        boolean exact = realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore);
        boolean sameDiff = realHomeScore - realAwayScore == predictHomeScore - predictAwayScore;

        return switch (m) {
            case EPL -> {
                if (exact) {
                    yield 4;
                }
                if (sameDiff) {
                    yield 2;
                }
                if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
                    yield 1;
                }
                if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
                    yield 1;
                }
                yield -1;
            }
            case EPL_WEEK_BONUS -> {
                if (exact) {
                    yield 5;
                }
                if (sameDiff) {
                    yield 3;
                }
                if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
                    yield 2;
                }
                if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
                    yield 2;
                }
                yield 0;
            }
            case CUP -> {
                if (exact) {
                    yield 2;
                }
                if (sameDiff
                    || (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore)
                    || (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore)) {
                    yield 1;
                }
                yield 0;
            }
        };
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

    public List<Prediction> getByBonusMatchPublicId(int publicId) {
        return predictionDao.findAllByBonusMatchId(publicId);
    }

    public Map<Integer, Prediction> predictionsByMatchForUser(String telegramId, Collection<Integer> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = matchIds instanceof List<Integer> list ? list : List.copyOf(matchIds);
        return predictionDao.findAllByMatchIdsAndTelegramId(ids, telegramId).stream()
                .collect(Collectors.toMap(Prediction::getMatchPublicId, p -> p, (a, b) -> a));
    }

    public Map<Integer, Prediction> predictionsByBonusMatchForUser(String telegramId, Collection<Integer> bonusIds) {
        if (bonusIds == null || bonusIds.isEmpty()) {
            return Map.of();
        }
        return predictionDao.findAllByBonusMatchIdsAndTelegramId(List.copyOf(bonusIds), telegramId).stream()
                .filter(p -> p.getBonusMatchId() != null)
                .collect(Collectors.toMap(Prediction::getBonusMatchId, p -> p, (a, b) -> a));
    }

    public List<Prediction> getAllByMatches(List<Match> matches) {
        return predictionDao.getAllByMatches(matches);
    }

    private ScoringMode scoringModeForEpl(int userId, Match match) {
        if (match == null) {
            return ScoringMode.EPL;
        }
        if (userWeekBonusMatchService.isWeekBonusMatch(userId, match.getWeekId(), match.getPublicId())) {
            return ScoringMode.EPL_WEEK_BONUS;
        }
        return ScoringMode.EPL;
    }

    private void updatePredictions(Match match, List<User> users) {
        List<Prediction> predictions = getByMatchPublicId(match.getPublicId());
        Map<Integer, Prediction> byUser = predictions.stream()
                .collect(Collectors.toMap(Prediction::getUserId, p -> p, (a, b) -> a));
        List<PredictionDao.PointsUpdate> updates = new ArrayList<>();
        for (User user : users) {
            Prediction prediction = byUser.get(user.getId());
            ScoringMode mode = scoringModeForEpl(user.getId(), match);
            int points = computePoints(
                    mode,
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    prediction != null ? prediction.getHomeTeamScore() : null,
                    prediction != null ? prediction.getAwayTeamScore() : null
            );
            updates.add(new PredictionDao.PointsUpdate(match.getPublicId(), user.getId(), points));
        }
        predictionDao.updatePointsBatch(updates);
    }

    public void updateByBonusMatch(BonusMatch match) {
        if (match == null) {
            return;
        }
        List<User> users = userService.findAll();
        List<Prediction> predictions = getByBonusMatchPublicId(match.getPublicId());
        Map<Integer, Prediction> byUser = predictions.stream()
                .collect(Collectors.toMap(Prediction::getUserId, p -> p, (a, b) -> a));
        if (!predictions.isEmpty() && predictions.size() < users.size()) {
            for (User user : users) {
                if (!byUser.containsKey(user.getId())) {
                    save(Prediction.builder()
                            .bonusMatchId(match.getPublicId())
                            .matchPublicId(0)
                            .points(0)
                            .homeTeamScore(null)
                            .awayTeamScore(null)
                            .userId(user.getId())
                            .build());
                }
            }
            predictions = getByBonusMatchPublicId(match.getPublicId());
            byUser = predictions.stream()
                    .collect(Collectors.toMap(Prediction::getUserId, p -> p, (a, b) -> a));
        }
        List<PredictionDao.BonusPointsUpdate> updates = new ArrayList<>();
        for (User user : users) {
            Prediction prediction = byUser.get(user.getId());
            int points = computePoints(
                    ScoringMode.CUP,
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    prediction != null ? prediction.getHomeTeamScore() : null,
                    prediction != null ? prediction.getAwayTeamScore() : null
            );
            updates.add(new PredictionDao.BonusPointsUpdate(match.getPublicId(), user.getId(), points));
        }
        predictionDao.updateBonusPointsBatch(updates);
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
        for (BonusMatch bonus : bonusMatchDao.findFinished()) {
            updateByBonusMatch(bonus);
        }
        log.info("Recalculated points for {} finished EPL + cup matches", finished.size());
    }

    public void updateByMatch(Match match) {
        List<User> users = userService.findAll();
        if (match != null) {
            userWeekBonusMatchService.ensureAssignedForWeek(match.getWeekId());
            List<Prediction> predictions = getByMatchPublicId(match.getPublicId());
            if (!predictions.isEmpty() && predictions.size() < users.size()) {
                Set<Integer> predictedUserIds = predictions.stream()
                        .map(Prediction::getUserId)
                        .collect(Collectors.toCollection(HashSet::new));
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictedUserIds.contains(user.getId()))
                        .toList();
                for (User user : usersWithNoPredicts) {
                    ScoringMode mode = scoringModeForEpl(user.getId(), match);
                    int noBet = mode == ScoringMode.EPL_WEEK_BONUS ? -1 : -1;
                    save(Prediction.builder()
                            .matchPublicId(match.getPublicId())
                            .points(noBet)
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
        return flooredPointsService.flooredSeasonTotals().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public Map<String, Integer> getWeeklyUsersPoints(int weekId) {
        return flooredPointsService.flooredWeekTotals(weekId).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public List<MatchPrediction> getAllWeeklyPredictionsByUserTelegramId(int weekId, String telegramId) {
        return predictionDao.findAllByWeekIdAndUserTelegramId(weekId, telegramId);
    }

    public List<Integer> getPredictableWeeksByUserTelegramId(String telegramId) {
        return predictionDao.findPredictableWeeksByUserTelegramId(telegramId);
    }

    public Map<String, Map<Integer, Integer>> getAllUsersCumulativePoints() {
        return flooredPointsService.flooredCumulativeByWeek();
    }
}
