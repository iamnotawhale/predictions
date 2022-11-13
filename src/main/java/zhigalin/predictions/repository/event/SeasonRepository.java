package zhigalin.predictions.repository.event;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Season;

@Repository
public interface SeasonRepository extends CrudRepository<Season, Long> {

    Season getBySeasonName(String seasonName);
}
