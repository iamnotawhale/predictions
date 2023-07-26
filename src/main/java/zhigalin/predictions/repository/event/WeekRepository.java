package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;

import java.util.List;

@Repository
public interface WeekRepository extends JpaRepository<Week, Long> {
    Week findByNameAndSeasonId(String name, Long seasonId);
    Week findByIsCurrentTrue();
    List<Week> findBySeasonIsCurrent(boolean current);
}
