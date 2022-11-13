package zhigalin.predictions.repository.football;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Standing;

@Repository
public interface StandingRepository extends CrudRepository<Standing, Long> {

    Standing getByTeam_Id(Long id);
}
