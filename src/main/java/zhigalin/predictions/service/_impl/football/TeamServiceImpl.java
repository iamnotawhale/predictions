package zhigalin.predictions.service._impl.football;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamRepository;
import zhigalin.predictions.service.football.TeamService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class TeamServiceImpl implements TeamService {

    private final TeamRepository repository;
    private final TeamMapper mapper;

    @Override
    public TeamDto save(TeamDto teamDto) {
        Team team = repository.findByTeamName(teamDto.getTeamName());
        if (team != null) {
            return mapper.toDto(team);
        }
        return mapper.toDto(repository.save(mapper.toEntity(teamDto)));
    }

    @Override
    public List<TeamDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public TeamDto findById(Long teamId) {
        return mapper.toDto(repository.findById(teamId).orElse(null));
    }

    @Override
    public TeamDto findByName(String teamName) {
        Team team = repository.findByTeamName(teamName);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto findByCode(String teamCode) {
        Team team = repository.findByCode(teamCode);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto findByPublicId(Long publicId) {
        Team team = repository.findByPublicId(publicId);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }
}
