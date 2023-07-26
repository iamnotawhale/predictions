package zhigalin.predictions.service.football;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamRepository;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class TeamService {
    private final TeamRepository repository;

    public Team save(Team team) {
        Team teamFromDB = repository.findByName(team.getName());
        if (teamFromDB != null) {
            return teamFromDB;
        }
        return repository.save(team);
    }

    public List<Team> findAll() {
        return repository.findAll();
    }

    public Team findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Team findByName(String name) {
        return repository.findByName(name);
    }

    public Team findByCode(String code) {
        return repository.findByCode(code);
    }

    public Team findByPublicId(Long publicId) {
        return repository.findByPublicId(publicId);
    }
}
