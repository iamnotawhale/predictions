package zhigalin.predictions.repository.predict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Points;

import java.util.List;

@Repository
public interface PointsRepository extends JpaRepository<Points, Long> {
    Points getPointsByUserIdAndSeasonIsCurrentTrue(Long userId);

    List<Points> findAllBySeasonIsCurrentTrue();

    @Query("select sum(p.points) " +
            "from Prediction p left join Match m on p.match.id = m.id " +
            "where m.week.wid = :weekId and p.user.id = :userId " +
            "and m.week.season.isCurrent = true " +
            "group by p.user")
    Long getPointsByUserIdAndMatchWeekWid(@Param("userId") Long userId, @Param("weekId") Long weekId);
    @Query("select sum(points) from Prediction where user.id = :userId and season.isCurrent = true group by user")
    Long getAllPointsByUserId(@Param("userId") Long userId);
}
