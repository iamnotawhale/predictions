package zhigalin.predictions.repository.football;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Team;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Team findByName(String name);

    Team findByCode(String code);

    Team findByPublicId(Long publicId);
}
