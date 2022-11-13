package zhigalin.predictions.service._impl.football;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.StandingRepository;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
public class StandingServiceImpl implements StandingService {

    private final StandingRepository repository;
    private final StandingMapper mapper;
    private final MatchService matchService;

    @Override
    public List<StandingDto> getAll() {
        List<StandingDto> list = currentTable();
        return list.stream()
                .sorted(Comparator.comparing(StandingDto::getPoints).reversed()
                        .thenComparing(StandingDto::compareGoals)
                        .thenComparing((s1, s2) -> s2.getGoalsScored().compareTo(s1.getGoalsScored())))
                .toList();
    }

    @Override
    public StandingDto save(StandingDto dto) {
        Standing standing = repository.getByTeam_Id(dto.getTeam().getId());
        if (standing != null) {
            mapper.updateEntityFromDto(dto, standing);
            return mapper.toDto(repository.save(standing));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public StandingDto findByTeam_Id(Long id) {
        Standing standing = repository.getByTeam_Id(id);
        if (standing != null) {
            return mapper.toDto(standing);
        }
        return null;
    }

    public List<StandingDto> currentTable() {
        List<StandingDto> list = new ArrayList<>();
        for (long i = 1L; i <= 20L; i++) {
            long teamId = i;
            List<MatchDto> allMatchesByTeamId = matchService.getAllByTeamId(teamId).stream()
                    .filter(m -> !m.getStatus().equals("ns"))
                    .filter(m -> !m.getStatus().equals("pst"))
                    .toList();

            Team team = allMatchesByTeamId.stream()
                    .map(m -> m.getHomeTeam().getId().equals(teamId) ? m.getHomeTeam() : m.getAwayTeam())
                    .findAny()
                    .orElse(null);

            StandingDto sDto = StandingDto.builder()
                    .id(teamId)
                    .games(0)
                    .points(0)
                    .won(0)
                    .draw(0)
                    .lost(0)
                    .team(team)
                    .goalsScored(0)
                    .goalsAgainst(0)
                    .build();

            for (MatchDto dto : allMatchesByTeamId) {
                sDto = updateByMatch(sDto, dto);
            }
            list.add(sDto);
        }
        return list;
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
        } else if (result.equals("D") && currentTeam.equals(homeTeam)) {
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
        } else if (result.equals("D") && currentTeam.equals(awayTeam)) {
            return StandingDto.builder()
                    .team(currentTeam)
                    .points(standingDto.getPoints() + 1)
                    .games(standingDto.getGames() + 1)
                    .won(standingDto.getWon())
                    .draw(standingDto.getDraw() + 1)
                    .lost(standingDto.getLost())
                    .goalsScored(standingDto.getGoalsScored() + matchDto.getAwayTeamScore())
                    .goalsAgainst(standingDto.getGoalsAgainst() + matchDto.getHomeTeamScore())
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
