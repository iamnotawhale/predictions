package zhigalin.predictions.service.football;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.repository.football.TeamDao;

@RequiredArgsConstructor
@Service
@Slf4j
public class TeamService {
    private final TeamDao teamDao;

    public void save(Team team) {
        teamDao.save(team);
    }

    public List<Team> findAll() {
        return teamDao.findAll();
    }

    public Team findByName(String name) {
        return teamDao.findByName(name);
    }

    public Team findByCode(String code) {
        return teamDao.findByCode(code);
    }

    public Team findByPublicId(int publicId) {
        return teamDao.findByPublicId(publicId);
    }
}
