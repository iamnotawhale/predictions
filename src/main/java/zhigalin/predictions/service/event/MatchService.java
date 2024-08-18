package zhigalin.predictions.service.event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.service.football.TeamService;


@Slf4j
@Service
public class MatchService {
    private final MatchDao matchDao;
    private final WeekService weekService;
    private final TeamService teamService;

    public MatchService(MatchDao matchDao, WeekService weekService, TeamService teamService) {
        this.matchDao = matchDao;
        this.weekService = weekService;
        this.teamService = teamService;
    }

    public void save(Match match) {
        matchDao.save(match);
    }

    public void update(Match matchToUpdate) {
        matchDao.updateMatch(matchToUpdate);
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
}

