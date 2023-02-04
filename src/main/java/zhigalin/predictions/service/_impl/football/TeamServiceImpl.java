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
    public TeamDto save(TeamDto dto) {
        Team team = repository.findByName(dto.getName());
        if (team != null) {
            return mapper.toDto(team);
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public List<TeamDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public TeamDto findById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public TeamDto findByName(String name) {
        Team team = repository.findByName(name);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto findByCode(String code) {
        Team team = repository.findByCode(code);
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
