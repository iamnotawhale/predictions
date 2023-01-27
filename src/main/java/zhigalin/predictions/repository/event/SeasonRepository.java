package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Season;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    Season findByName(String name);
}
