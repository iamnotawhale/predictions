package zhigalin.predictions.service.football;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.StandingRepository;
import zhigalin.predictions.service.event.MatchService;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
@Slf4j
public class StandingService {
    private final StandingRepository repository;
    private final MatchService matchService;
    private final TeamService teamService;
    private final Map<Long, Integer> places = new HashMap<>();

    public Standing save(Standing standing) {
        return repository.findByTeamId(standing.getTeam().getId()) != null ? null : repository.save(standing);
    }

    public void update(Standing standing) {
        Standing st = findByPublicId(standing.getTeam().getPublicId());
        if (st != null) {
            repository.update(standing.getTeam().getId(), standing.getGames(), standing.getPoints(), standing.getWon(),
                    standing.getDraw(), standing.getLost(), standing.getGoalsScored(), standing.getGoalsAgainst());
        }
    }

    public Standing findByPublicId(Long publicId) {
        return repository.findByTeamPublicId(publicId);
    }

    public List<Standing> findAll() {
        List<Standing> list;
        AtomicInteger place = new AtomicInteger(1);
        if (matchService.findOnline().isEmpty()) {
            list = repository.findAll();
        } else {
            list = currentOnlineTable();
        }
        list = list.stream()
                .sorted(Comparator.comparing(Standing::getPoints)
                        .reversed()
                        .thenComparing(Standing::compareGoals)
                        .thenComparing((s1, s2) -> s2.getGoalsScored().compareTo(s1.getGoalsScored()))
                        .thenComparing(s -> s.getTeam().getName()))
                .toList();
        list.forEach(st -> places.put(st.getTeam().getPublicId(), place.getAndIncrement()));
        return list;
    }

    public Map<Long, Integer> getPlaces() {
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
            Long teamId = team.getId();
            List<Match> allMatchesByTeam = allMatches.stream()
                    .filter(m -> m.getHomeTeam().getId().equals(teamId) || m.getAwayTeam().getId().equals(teamId))
                    .toList();
            Standing standing = Standing.builder()
                    .games(0)
                    .points(0)
                    .won(0)
                    .draw(0)
                    .lost(0)
                    .team(team)
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
        Team currentTeam = standing.getTeam();
        String result = match.getResult();
        Team homeTeam = match.getHomeTeam();
        Team awayTeam = match.getAwayTeam();
        if (result.equals("H") && currentTeam.equals(homeTeam)) {
            return Standing.builder()
                    .team(currentTeam)
                    .points(standing.getPoints() + 3)
                    .games(standing.getGames() + 1)
                    .won(standing.getWon() + 1)
                    .draw(standing.getDraw())
                    .lost(standing.getLost())
                    .goalsScored(standing.getGoalsScored() + match.getHomeTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getAwayTeamScore())
                    .build();
        } else if (result.equals("H") && currentTeam.equals(awayTeam)) {
            return Standing.builder()
                    .team(currentTeam)
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
                    .team(currentTeam)
                    .points(standing.getPoints() + 1)
                    .games(standing.getGames() + 1)
                    .won(standing.getWon())
                    .draw(standing.getDraw() + 1)
                    .lost(standing.getLost())
                    .goalsScored(standing.getGoalsScored() + match.getHomeTeamScore())
                    .goalsAgainst(standing.getGoalsAgainst() + match.getAwayTeamScore())
                    .build();
        } else if (result.equals("A") && currentTeam.equals(homeTeam)) {
            return Standing.builder()
                    .team(currentTeam)
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
                    .team(currentTeam)
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
