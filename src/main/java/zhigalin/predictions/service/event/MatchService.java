package zhigalin.predictions.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.MatchRepository;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class MatchService {
    private final MatchRepository repository;
    private final UserService userService;
    private final PredictionService predictionService;

    public Match save(Match match) {
        if (repository.findByPublicId(match.getPublicId()) == null) {
            return repository.save(match);
        }
        return null;
    }

    public void update(Match match) {
        Match m = repository.findByPublicId(match.getPublicId());
        List<User> users = userService.findAll();
        if (m != null) {
            List<Prediction> predictions = m.getPredictions();
            if (!predictions.isEmpty() && predictions.size() < 4) {
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictions.stream()
                                .map(prediction -> prediction.getUser().getId())
                                .toList()
                                .contains(user.getId()))
                        .toList();
                for (User user : usersWithNoPredicts) {
                    predictionService.save(Prediction.builder()
                            .match(match)
                            .points(-1L)
                            .homeTeamScore(null)
                            .awayTeamScore(null)
                            .user(user)
                            .build());
                }
            }
            updatePredictions(m, users);
            repository.updateMatch(match.getPublicId(), match.getHomeTeamScore(), match.getAwayTeamScore(),
                    match.getResult(), match.getStatus());
        }
    }

    public void forceUpdatePoints(Match match) {
        for (User user : userService.findAll()) {
            predictionService.updatePoints(match.getId(), user.getId());
        }
    }

    private void updatePredictions(Match m, List<User> users) {
        for (User user : users) {
            predictionService.updatePoints(m.getId(), user.getId());
        }
    }

    public void updateStatusAndLocalDateTime(Match match) {
        Match m = repository.findByPublicId(match.getPublicId());
        if (m != null) {
            m.setStatus(match.getStatus());
            m.setLocalDateTime(match.getLocalDateTime());
            repository.save(m);
        }
    }

    public Match findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Match findByPublicId(Long publicId) {
        return repository.findByPublicId(publicId);
    }

    public List<Match> findAllByTodayDate() {
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime
                        .of(LocalDate.now(), LocalTime.of(0, 1)),
                LocalDateTime
                        .of(LocalDate.now(), LocalTime.of(23, 59)));
    }

    public List<Match> findAllNearest(Long minutes) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(now, now.plusMinutes(minutes));
    }

    public List<Match> findAllByUpcomingDays(Integer days) {
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now(),
                LocalDateTime.now().plusDays(days));
    }

    public List<Match> findAllByWeekId(Long id) {
        return repository.findAllByWeekIdOrderByLocalDateTime(id);
    }

    public List<Match> findAllByCurrentWeek() {
        return repository.findAllByWeekIsCurrentTrueOrderByLocalDateTime();
    }

    public List<Match> findAll() {
        return repository.findAll();
    }

    public Match findByTeamNames(String homeTeamName, String awayTeamName) {
        return repository.findByHomeTeamNameAndAwayTeamName(homeTeamName, awayTeamName);
    }

    public Match findByTeamCodes(String homeTeamCode, String awayTeamCode) {
        return repository.findByHomeTeamCodeAndAwayTeamCode(homeTeamCode, awayTeamCode);
    }

    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        List<Integer> result = new ArrayList<>();
        Match match = repository.findByHomeTeamNameAndAwayTeamName(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }

    public List<Match> findAllByTeamId(Long id) {
        return repository.findAllByTeamId(id);
    }

    public List<Match> findLast5MatchesByTeamId(Long id) {
        return repository.findAllByTeamId(id)
                .stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .toList();
    }

    public List<String> getLast5MatchesResultByTeamId(Long id) {
        List<String> result = new ArrayList<>();
        List<Match> list = repository.findAllByTeamId(id).stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .toList();
        for (Match match : list) {
            if (match.getHomeTeam().getId().equals(id) && match.getResult().equals("H") ||
                    match.getAwayTeam().getId().equals(id) && match.getResult().equals("A")) {
                result.add("W");
            } else if (match.getHomeTeam().getId().equals(id) && match.getResult().equals("A") ||
                    match.getAwayTeam().getId().equals(id) && match.getResult().equals("H")) {
                result.add("L");
            } else {
                result.add("D");
            }
        }
        return result;
    }

    public List<Match> findOnline() {
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusMinutes(140),
                LocalDateTime.now().plusMinutes(20)).stream().filter(match -> !match.getStatus().equals("pst")).toList();
    }

    public List<Match> findAllByStatus(String status) {
        return repository.findAllByStatus(status);
    }

    public Match getOnlineResult(String teamName) {
        Match match = repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusHours(2),
                        LocalDateTime.now())
                .stream()
                .filter(m -> m.getHomeTeam().getName().equals(teamName) || m.getAwayTeam().getName().equals(teamName))
                .findFirst()
                .orElse(null);

        if (match != null && match.getResult() != null) {
            if (match.getHomeTeam().getName().equals(teamName)) {
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
        List<Match> allMatches = repository.findAll();
        List<User> users = userService.findAll();
        allMatches.stream()
                .filter(m -> m.getLocalDateTime().isBefore(LocalDateTime.now()))
                .filter(m -> m.getPredictions().size() < 4)
                .forEach(m -> {
                    List<Prediction> predictions = m.getPredictions();
                    if (!predictions.isEmpty()) {
                        List<User> usersWithNoPredicts = users.stream()
                                .filter(user -> !predictions.stream()
                                        .map(prediction -> prediction.getUser().getId())
                                        .toList()
                                        .contains(user.getId()))
                                .toList();
                        for (User user : usersWithNoPredicts) {
                            predictionService.save(Prediction.builder()
                                    .match(m)
                                    .points(-1L)
                                    .homeTeamScore(null)
                                    .awayTeamScore(null)
                                    .user(user)
                                    .build());
                        }
                    }
                });
    }
}
