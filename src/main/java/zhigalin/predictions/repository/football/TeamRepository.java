package zhigalin.predictions.repository.football;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Team;

@Repository
public interface TeamRepository extends CrudRepository<Team, Long> {

    Team getByTeamName(String teamName);

    Team getByCode(String code);

    Team getByPublicId(Long id);
}
