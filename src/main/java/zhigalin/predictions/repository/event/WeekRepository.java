package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;

@Repository
public interface WeekRepository extends JpaRepository<Week, Long> {

    Week findByWeekName(String weekName);

    Week findWeekByIsCurrentTrue();
}
