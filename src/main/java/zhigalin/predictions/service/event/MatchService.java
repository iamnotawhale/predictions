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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
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
        if (m != null) {
            List<Prediction> predictions = m.getPredictions();
            if (!predictions.isEmpty()) {
                List<User> users = userService.findAll();
                List<User> usersWithNoPredicts = users.stream()
                        .filter(user -> !predictions.stream()
                                .map(prediction -> prediction.getUser().getId())
                                .toList()
                                .contains(user.getId()))
                        .toList();
                for (User user : usersWithNoPredicts) {
                    predictionService.save(Prediction.builder()
                            .match(match)
                            .homeTeamScore(null)
                            .awayTeamScore(null)
                            .user(user)
                            .build());
                }
                updatePredictions(m, users);
            }
            repository.updateMatch(match.getPublicId(), match.getHomeTeamScore(), match.getAwayTeamScore(),
                    match.getResult(), match.getStatus());
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
            repository.updateStatusAndDateTime(match.getPublicId(), match.getStatus(), m.getLocalDateTime());
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

    public List<Match> findAllNearest() {
        LocalDateTime now = LocalDateTime.now();
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(now, now.plusMinutes(30));
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
}
