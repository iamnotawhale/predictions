package zhigalin.predictions.service_impl.football;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamRepository;
import zhigalin.predictions.service.football.TeamService;

@Service
public class TeamServiceImpl implements TeamService {

    private final TeamRepository repository;
    private final TeamMapper mapper;

    @Autowired
    public TeamServiceImpl(TeamRepository repository, TeamMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    @Override
    public TeamDto saveTeam(TeamDto teamDto) {
        Team savedTeam = repository.save(mapper.toEntity(teamDto));
        return mapper.toDto(savedTeam);
    }

    @Override
    public TeamDto getById(Long id) {
        return mapper.toDto(repository.findById(id).get());
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
}
