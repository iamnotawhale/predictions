package zhigalin.predictions.service.event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MatchService {
    private final MatchDao matchDao;
    private final TeamService teamService;

    @Getter
    private final Map<Integer, Integer> places = new HashMap<>();

    public MatchService(MatchDao matchDao, TeamService teamService) {
        this.matchDao = matchDao;
        this.teamService = teamService;
    }

    public void save(List<Match> matches) {
        matchDao.save(matches);
    }

    public void updateAll(List<Match> matches) {
        matchDao.updateMatches(matches);
    }

    public void update(Match matchToUpdate) {
        matchDao.updateMatches(List.of(matchToUpdate));
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
        return matchDao.findAllByCurrentWeek();
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
                .limit(6)
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

    public List<Standing> getStandings() {
        List<Standing> standings = matchDao.getStandings();
        AtomicInteger place = new AtomicInteger(1);
        standings.forEach(standing -> places.put(standing.getTeamId(), place.getAndIncrement()));
        return standings;
    }

    public List<Match> findOnlineMatches() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusMinutes(140);
        LocalDateTime to = now.plusMinutes(20);
        return matchDao.findAllBetweenToDates(from, to).stream()
                .filter(match -> !match.getStatus().equals("pst"))
                .toList();
    }

    public Match getOnlineResult(int teamId) {
        Team team = DaoUtil.TEAMS.get(teamId);
        Match match = matchDao.findOnlineMatchByTeamId(teamId);
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

    public List<Integer> predictableMatchesByUserTelegramIdAndWeekId(String telegramId, int weekId) {
        return matchDao.getPredictableMatchIdsByUserTelegramAndWeek(telegramId, weekId);
    }

    public List<Integer> predictableTodayMatchesByUserTelegramIdAndWeekId(String telegramId) {
        return matchDao.getPredictableTodayMatchIdsByUserTelegram(telegramId);
    }

    public List<Match> findBetweenTwoDates(LocalDateTime from, LocalDateTime to) {
        return matchDao.findAllBetweenToDates(from, to);
    }

    public void listenForMatchUpdates() {
        matchDao.listenForMatchUpdates();
    }

    public List<Match> processBatch() {
        return matchDao.processBatch();
    }
}

