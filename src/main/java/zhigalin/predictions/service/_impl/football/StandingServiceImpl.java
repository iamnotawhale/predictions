package zhigalin.predictions.service._impl.football;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.StandingRepository;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
public class StandingServiceImpl implements StandingService {

    private final StandingRepository repository;
    private final StandingMapper mapper;
    private final MatchService matchService;
    private final TeamService teamService;
    private final TeamMapper teamMapper;

    @Override
    public StandingDto save(StandingDto dto) {
        Standing standing = repository.findByTeamId(dto.getTeam().getId());
        if (standing != null) {
            mapper.updateEntityFromDto(dto, standing);
            return mapper.toDto(repository.save(standing));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public List<StandingDto> findAll() {
        List<StandingDto> list;
        if (matchService.findOnline().isEmpty()) {
            list = repository.findAll().stream()
                    .map(mapper::toDto)
                    .toList();
        } else {
            list = currentOnlineTable();
        }
        return list.stream()
                .sorted(Comparator.comparing(StandingDto::getPoints)
                        .reversed()
                        .thenComparing(StandingDto::compareGoals)
                        .thenComparing((s1, s2) -> s2.getGoalsScored().compareTo(s1.getGoalsScored())))
                .toList();
    }

    public List<StandingDto> currentOnlineTable() {
        List<StandingDto> currentTable = new ArrayList<>();
        List<TeamDto> allTeams = teamService.findAll();
        List<MatchDto> allMatches = matchService.findAll().stream()
                .filter(m -> !m.getStatus().equals("ns"))
                .filter(m -> !m.getStatus().equals("pst"))
                .toList();

        for (TeamDto teamDto : allTeams) {
            Long teamId = teamDto.getId();
            List<MatchDto> allMatchesByTeam = allMatches.stream()
                    .filter(m -> m.getHomeTeam().getId().equals(teamId) || m.getAwayTeam().getId().equals(teamId))
                    .toList();
            StandingDto sDto = StandingDto.builder()
                    .games(0)
                    .points(0)
                    .won(0)
                    .draw(0)
                    .lost(0)
                    .team(teamMapper.toEntity(teamDto))
                    .goalsScored(0)
                    .goalsAgainst(0)
                    .build();
            for (MatchDto matchDto : allMatchesByTeam) {
                sDto = updateByMatch(sDto, matchDto);
            }
            currentTable.add(sDto);
            save(sDto);
        }
        return currentTable;
    }

    public StandingDto updateByMatch(StandingDto standingDto, MatchDto matchDto) {
        Team currentTeam = standingDto.getTeam();
        String result = matchDto.getResult();
        Team homeTeam = matchDto.getHomeTeam();
        Team awayTeam = matchDto.getAwayTeam();
        if (result.equals("H") && currentTeam.equals(homeTeam)) {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints() + 3)
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon() + 1)
                    .draw(standingDto.getDraw())
                    .lost(standingDto.getLost())
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getHomeTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getAwayTeamScore())
                    .build();
        } else if (result.equals("H") && currentTeam.equals(awayTeam)) {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints())
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon())
                    .draw(standingDto.getDraw())
                    .lost(standingDto.getLost() + 1)
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getAwayTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getHomeTeamScore())
                    .build();
        } else if (result.equals("D")) {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints() + 1)
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon())
                    .draw(standingDto.getDraw() + 1)
                    .lost(standingDto.getLost())
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getHomeTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getAwayTeamScore())
                    .build();
        } else if (result.equals("A") && currentTeam.equals(homeTeam)) {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints())
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon())
                    .draw(standingDto.getDraw())
                    .lost(standingDto.getLost() + 1)
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getHomeTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getAwayTeamScore())
                    .build();
        } else {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints() + 3)
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon() + 1)
                    .draw(standingDto.getDraw())
                    .lost(standingDto.getLost())
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getAwayTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getHomeTeamScore())
                    .build();
        }
    }
}
