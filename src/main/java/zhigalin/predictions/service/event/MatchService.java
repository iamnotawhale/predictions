package zhigalin.predictions.service.event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;


@Slf4j
@Service
public class MatchService {
    private final MatchDao matchDao;
    private final UserService userService;
    private final PredictionService predictionService;
    private final WeekService weekService;
    private final TeamService teamService;

    public MatchService(MatchDao matchDao, UserService userService, PredictionService predictionService,
                        WeekService weekService, TeamService teamService) {
        this.matchDao = matchDao;
        this.userService = userService;
        this.predictionService = predictionService;
        this.weekService = weekService;
        this.teamService = teamService;
    }

    public void save(Match match) {
        matchDao.save(match);
    }

    public void update(int publicId) {
        Match match = matchDao.findByPublicId(publicId);
        List<User> users = userService.findAll();
        if (match != null) {
            List<Prediction> predictions = predictionService.getByMatchPublicId(match.getPublicId());
            if (!predictions.isEmpty() && predictions.size() < 4) {
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictions.stream()
                                .map(Prediction::getUserId)
                                .toList()
                                .contains(user.getId()))
                        .toList();
                for (User user : usersWithNoPredicts) {
                    predictionService.save(Prediction.builder()
                            .matchPublicId(match.getPublicId())
                            .points(-1)
                            .homeTeamScore(null)
                            .awayTeamScore(null)
                            .userId(user.getId())
                            .build());
                }
            }
            updatePredictions(match, users);
            matchDao.updateMatch(match);
        }
    }

    public void forceUpdatePoints(Match match) {
        for (User user : userService.findAll()) {
            predictionService.updatePoints(match.getPublicId(), user.getId());
        }
    }

    private void updatePredictions(Match match, List<User> users) {
        for (User user : users) {
            predictionService.updatePoints(match.getPublicId(), user.getId());
        }
    }

    public void updateStatusAndLocalDateTime(int matchPublicId, String status, LocalDateTime matchDateTime) {
        Match match = matchDao.findByPublicId(matchPublicId);
        if (match != null) {
            match.setStatus(match.getStatus());
            match.setLocalDateTime(match.getLocalDateTime());
            matchDao.save(match);
        }
    }

    public Match findByPublicId(int publicId) {
        return matchDao.findByPublicId(publicId);
    }

    public List<Match> findAllByTodayDate() {
        return matchDao.findAllTodayMatches();
    }

    public List<Match> findAllNearest(int minutes) {
        return matchDao.findAllMatchesInTheNextMinutes(minutes);
    }

    public List<Match> findAllByUpcomingDays(int days) {
        return matchDao.findAllMatchesInTheNextMinutes(days * 24 * 60);
    }

    public List<Match> findAllByWeekId(int weekId) {
        return matchDao.findAllByWeekIdOrderByLocalDateTime(weekId);
    }

    public List<Match> findAllByCurrentWeek() {
        Week currentWeek = weekService.findCurrentWeek();
        return matchDao.findAllByWeekIdOrderByLocalDateTime(currentWeek.getId());
    }

    public List<Match> findAll() {
        return matchDao.findAll();
    }

    public Match findByTeamNames(String homeTeamName, String awayTeamName) {
        Team homeTeam = teamService.findByName(homeTeamName);
        Team awayTeam = teamService.findByName(awayTeamName);
        return matchDao.findMatchByTeamsPublicId(homeTeam.getPublicId(), awayTeam.getPublicId());
    }

    public Match findByTeamCodes(String homeTeamCode, String awayTeamCode) {
        Team homeTeam = teamService.findByCode(homeTeamCode);
        Team awayTeam = teamService.findByCode(awayTeamCode);
        return matchDao.findMatchByTeamsPublicId(homeTeam.getPublicId(), awayTeam.getPublicId());
    }

    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        List<Integer> result = new ArrayList<>();
        Match match = findByTeamNames(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }

    public List<Match> findLast5MatchesByTeamId(int teamPublicId) {
        return matchDao.findAllByTeamPublicId(teamPublicId).stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .toList();
    }

    public List<String> getLast5MatchesResultByTeamId(int teamPublicId) {
        List<String> result = new ArrayList<>();
        List<Match> list = findLast5MatchesByTeamId(teamPublicId);
        for (Match match : list) {
            if (match.getHomeTeamId() == teamPublicId && match.getResult().equals("H") ||
                    match.getAwayTeamId() == teamPublicId && match.getResult().equals("A")) {
                result.add("W");
            } else if (match.getHomeTeamId() == teamPublicId && match.getResult().equals("A") ||
                    match.getAwayTeamId() == teamPublicId && match.getResult().equals("H")) {
                result.add("L");
            } else {
                result.add("D");
            }
        }
        return result;
    }

    public List<Match> findOnline() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusMinutes(140);
        LocalDateTime to = now.plusMinutes(20);
        return matchDao.findAllBetweenToDates(from, to).stream()
                .filter(match -> !match.getStatus().equals("pst"))
                .toList();
    }

    public List<Match> findAllByStatus(String status) {
        return matchDao.findAllByStatus(status);
    }

    public Match getOnlineResult(String teamName) {
        Team team = teamService.findByName(teamName);
        Match match = matchDao.findAllBetweenToDates(LocalDateTime.now().minusHours(2), LocalDateTime.now())
                .stream()
                .filter(m -> m.getHomeTeamId() == team.getPublicId() || m.getAwayTeamId() == team.getPublicId())
                .findFirst()
                .orElse(null);

        if (match != null && match.getResult() != null) {
            if (match.getHomeTeamId() == team.getPublicId()) {
                return Match.builder()
                        .homeTeamScore(match.getHomeTeamScore())
                        .awayTeamScore(match.getAwayTeamScore())
                        .result(Objects.equals(match.getResult(), "H") ? "H" :
                                Objects.equals(match.getResult(), "A") ? "A" : "D")
                        .build();
            } else {
                return Match.builder()
                        .homeTeamScore(match.getAwayTeamScore())
                        .awayTeamScore(match.getHomeTeamScore())
                        .result(Objects.equals(match.getResult(), "A") ? "H" :
                                Objects.equals(match.getResult(), "H") ? "A" : "D")
                        .build();
            }
        }
        return null;
    }

    public void updateUnpredictableMatches() {
        List<Match> allMatches = matchDao.findAll().stream()
                .filter(match -> !match.getStatus().equals("pst") && match.getLocalDateTime().isBefore(LocalDateTime.now()))
                .toList();
        List<User> users = userService.findAll();

        predictionService.getAllByMatches(allMatches).stream()
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
                                    predictionService.save(
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
}

