package zhigalin.predictions.repository.football;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Standing;

@Repository
public interface StandingRepository extends JpaRepository<Standing, Long> {

    Standing findByTeamId(Long id);
}
