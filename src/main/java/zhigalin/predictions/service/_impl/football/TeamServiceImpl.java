package zhigalin.predictions.service._impl.football;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamRepository;
import zhigalin.predictions.service.football.TeamService;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
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
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public TeamDto findById(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public TeamDto findByName(String name) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Team team = repository.findByName(name);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto findByCode(String code) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Team team = repository.findByCode(code);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }

    @Override
    public TeamDto findByPublicId(Long publicId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Team team = repository.findByPublicId(publicId);
        if (team != null) {
            return mapper.toDto(team);
        }
        return null;
    }
}
