package zhigalin.predictions.service.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MatchService {
    private final MatchDao matchDao;

    @Getter
    private final Map<Integer, Integer> places = new HashMap<>();

    public MatchService(MatchDao matchDao) {
        this.matchDao = matchDao;
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
        return sorted(matchDao.findAllTodayMatches());
    }

    public List<Match> findAllByDate(LocalDate date) {
        return sorted(matchDao.findAllMatchesByDate(date));
    }

    public List<Match> findAllNearest(int minutes) {
        return sorted(matchDao.findAllMatchesInTheNextMinutes(minutes));
    }

    public List<Match> findAllByUpcomingDays(int days) {
        return sorted(matchDao.findAllMatchesInTheNextMinutes(days * 24 * 60));
    }

    public List<Match> findAllByWeekId(int weekId) {
        return sorted(matchDao.findAllByWeekIdOrderByLocalDateTime(weekId));
    }

    public List<Match> findAllByCurrentWeek() {
        return sorted(matchDao.findAllByCurrentWeek());
    }

    public List<Match> findAll() {
        return sorted(matchDao.findAll());
    }

    public Match findByTeamCodes(String homeTeamCode, String awayTeamCode) {
        Integer homeId = null;
        Integer awayId = null;
        for (Team team : DaoUtil.TEAMS.values()) {
            if (homeId == null && team.getCode().equals(homeTeamCode)) {
                homeId = team.getPublicId();
            }
            if (awayId == null && team.getCode().equals(awayTeamCode)) {
                awayId = team.getPublicId();
            }
            if (homeId != null && awayId != null) {
                break;
            }
        }
        if (homeId == null || awayId == null) {
            throw new IllegalArgumentException("Unknown team code(s): " + homeTeamCode + "/" + awayTeamCode);
        }
        return matchDao.findMatchByTeamsPublicId(homeId, awayId);
    }

    public Match findByTeamIds(Integer home, Integer away) {
        return matchDao.findMatchByTeamsPublicId(home, away);
    }

    public List<Match> findAllByTeamPublicId(int teamPublicId) {
        return sorted(matchDao.findAllByTeamPublicId(teamPublicId));
    }

    public List<Match> findLast5MatchesByTeamId(int teamPublicId) {
        return findLastFinishedByTeamId(teamPublicId, 5);
    }

    public List<Match> findLastFinishedByTeamId(int teamPublicId, int limit) {
        return matchDao.findLastFinishedByTeamPublicId(teamPublicId, limit);
    }

    public List<Match> findNextByTeamId(int teamPublicId, int limit) {
        return matchDao.findNextByTeamPublicId(teamPublicId, limit);
    }

    public boolean hasPostponedMatches() {
        return matchDao.hasPostponedMatches();
    }

    public List<Match> findFinishedMatches() {
        return sorted(matchDao.findFinishedMatches());
    }

    public List<Match> findPastNonPostponedMatches() {
        return sorted(matchDao.findPastNonPostponedMatches());
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
        return sorted(matchDao.findAllBetweenToDates(from, to));
    }

    public List<Integer> predictableMatchesByUserTelegramIdAndWeekId(String telegramId, int weekId) {
        return matchDao.getPredictableMatchIdsByUserTelegramAndWeek(telegramId, weekId);
    }

    public List<Integer> predictableTodayMatchesByUserTelegramIdAndWeekId(String telegramId) {
        return matchDao.getPredictableTodayMatchIdsByUserTelegram(telegramId);
    }

    public void updateLiveScoreMessageId(int publicId, Integer messageId) {
        matchDao.updateLiveScoreMessageId(publicId, messageId);
    }

    public void listenForMatchUpdates() {
        matchDao.listenForMatchUpdates();
    }

    public List<Match> processBatch() {
        return sorted(matchDao.processBatch());
    }

    private static List<Match> sorted(List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return matches == null ? List.of() : matches;
        }
        return matches.stream().sorted(Match.BY_KICKOFF_THEN_PUBLIC_ID).toList();
    }
}

