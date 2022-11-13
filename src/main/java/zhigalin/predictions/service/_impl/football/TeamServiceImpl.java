package zhigalin.predictions.service._impl.football;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamRepository;
import zhigalin.predictions.service.football.TeamService;

@RequiredArgsConstructor
@Service
public class TeamServiceImpl implements TeamService {

    private final TeamRepository repository;
    private final TeamMapper mapper;

    @Override
    public TeamDto saveTeam(TeamDto teamDto) {
        Team team = repository.getByTeamName(teamDto.getTeamName());
        if (team != null) {
            return mapper.toDto(team);
        }
        return mapper.toDto(repository.save(mapper.toEntity(teamDto)));
    }

    @Override
    public TeamDto getById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public TeamDto getByName(String teamName) {
        Team team = repository.getByTeamName(teamName);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto getByCode(String code) {
        return mapper.toDto(repository.getByCode(code));
    }

    @Override
    public TeamDto getByPublicId(Long id) {
        Team team = repository.getByPublicId(id);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }
}
