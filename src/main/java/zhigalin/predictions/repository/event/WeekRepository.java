package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;

@Repository
public interface WeekRepository extends CrudRepository<Week, Long> {

    Week getByWeekName(String weekName);

    @Query("select w from Week w where w.isCurrent is true")
    Week getCurrentWeek();
}
