package zhigalin.predictions.repository.predict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Points;

@Repository
public interface PointsRepository extends JpaRepository<Points, Long> {
    Points getPointsByUserId(Long userId);
    @Query("select sum(p.points) " +
            "from Prediction p left join Match m on p.match.id = m.id " +
            "where m.week.id = :weekId and p.user.id = :userId " +
            "group by p.user")
    Long getPointsByUserIdAndMatchWeekId(@Param("userId") Long userId, @Param("weekId") Long weekId);
    @Query("select sum(points) from Prediction where user.id = :userId group by user")
    Long getAllPointsByUserId(@Param("userId") Long userId);
}
