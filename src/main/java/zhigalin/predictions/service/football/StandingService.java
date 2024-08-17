package zhigalin.predictions.service.football;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.StandingDao;
import zhigalin.predictions.service.event.MatchService;

@RequiredArgsConstructor
@Service
@Slf4j
public class StandingService {
    private final StandingDao standingDao;
    private final MatchService matchService;
    private final TeamService teamService;
    private final Map<Integer, Integer> places = new HashMap<>();

    public void save(Standing standing) {
        standingDao.save(standing);
    }

    public void update(Standing standing) {
        standingDao.update(standing);
    }

    public Standing findByPublicId(int publicId) {
        return standingDao.findByTeamPublicId(publicId);
    }

    public List<Standing> findAll() {
        List<Standing> list;
        AtomicInteger place = new AtomicInteger(1);
        if (matchService.findOnline().isEmpty()) {
            list = standingDao.findAll();
        } else {
            list = currentOnlineTable();
        }
        list = list.stream()
                .sorted(Comparator.comparing(Standing::getPoints)
                        .reversed()
                        .thenComparing(Standing::compareGoals)
                        .thenComparing(Standing::getGoalsScored)
                        .thenComparing(Standing::getTeamId))
                .toList();
        list.forEach(st -> places.put(st.getTeamId(), place.getAndIncrement()));
        return list;
    }

    public Map<Integer, Integer> getPlaces() {
        if (places.isEmpty()) {
            findAll();
        }
        return places;
    }

    public List<Standing> currentOnlineTable() {
        List<Standing> currentTable = new ArrayList<>();
        List<Team> allTeams = teamService.findAll();
        List<Match> allMatches = matchService.findAll().stream()
                .filter(m -> !m.getStatus().equals("ns"))
                .filter(m -> !m.getStatus().equals("pst"))
                .toList();

        for (Team team : allTeams) {
            int teamId = team.getPublicId();
            List<Match> allMatchesByTeam = allMatches.stream()
                    .filter(m -> m.getHomeTeamId() == teamId || m.getAwayTeamId() == teamId)
                    .toList();
            Standing standing = Standing.builder()
                    .games(0)
                    .points(0)
                    .won(0)
                    .draw(0)
                    .lost(0)
                    .teamId(teamId)
                    .goalsScored(0)
                    .goalsAgainst(0)
                    .build();
            for (Match match : allMatchesByTeam) {
                standing = updateByMatch(standing, match);
            }
            currentTable.add(standing);
            update(standing);
        }
        return currentTable;
    }

    public Standing updateByMatch(Standing standing, Match match) {
        int teamId = standing.getTeamId();
        String result = match.getResult();
        int homeTeamId = match.getHomeTeamId();
        int awayTeamId = match.getAwayTeamId();
        if (result.equals("H") && teamId == homeTeamId) {
            return Standing.builder()
                    .teamId(teamId)
                    .points(standing.getPoints() + 3)
                    .games(standing.getGames() + 1)
                    .won(standing.getWon() + 1)
                    .draw(standing.getDraw())
                    .lost(standing.getLost())
                    .goalsScored(standing.getGoalsScored() + match.getHomeTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getAwayTeamScore())
                    .build();
        } else if (result.equals("H") && teamId == awayTeamId) {
            return Standing.builder()
                    .teamId(teamId)
                    .points(standing.getPoints())
                    .games(standing.getGames() + 1)
                    .won(standing.getWon())
                    .draw(standing.getDraw())
                    .lost(standing.getLost() + 1)
                    .goalsScored(standing.getGoalsScored() + match.getAwayTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getHomeTeamScore())
                    .build();
        } else if (result.equals("D")) {
            return Standing.builder()
                    .teamId(teamId)
                    .points(standing.getPoints() + 1)
                    .games(standing.getGames() + 1)
                    .won(standing.getWon())
                    .draw(standing.getDraw() + 1)
                    .lost(standing.getLost())
                    .goalsScored(standing.getGoalsScored() + match.getHomeTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getAwayTeamScore())
                    .build();
        } else if (result.equals("A") && teamId == homeTeamId) {
            return Standing.builder()
                    .teamId(teamId)
                    .points(standing.getPoints())
                    .games(standing.getGames() + 1)
                    .won(standing.getWon())
                    .draw(standing.getDraw())
                    .lost(standing.getLost() + 1)
                    .goalsScored(standing.getGoalsScored() + match.getHomeTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getAwayTeamScore())
                    .build();
        } else {
            return Standing.builder()
                    .teamId(teamId)
                    .points(standing.getPoints() + 3)
                    .games(standing.getGames() + 1)
                    .won(standing.getWon() + 1)
                    .draw(standing.getDraw())
                    .lost(standing.getLost())
                    .goalsScored(standing.getGoalsScored() + match.getAwayTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getHomeTeamScore())
                    .build();
        }
    }
}
