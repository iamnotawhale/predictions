package zhigalin.predictions.repository.football;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Standing;

import java.util.List;

@Repository
public interface StandingRepository extends JpaRepository<Standing, Long> {
    Standing findByTeamIdAndSeasonIsCurrentTrue(Long id);
    List<Standing> findAllBySeasonIsCurrentTrue();
}
