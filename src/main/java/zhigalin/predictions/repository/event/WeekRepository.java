package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;

import javax.transaction.Transactional;

@Repository
public interface WeekRepository extends JpaRepository<Week, Long> {
    Week findByNameAndSeasonId(String name, Long seasonId);
    Week findByIsCurrentTrue();
    @Transactional
    @Modifying
    @Query("update Week w set w.isCurrent = :isCurrent where w.id = :id")
    void updateCurrentWeek(Long id, Boolean isCurrent);
}
