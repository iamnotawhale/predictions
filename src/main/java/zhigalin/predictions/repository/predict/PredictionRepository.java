package zhigalin.predictions.repository.predict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Prediction;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    Prediction findByMatchIdAndUserId(Long matchId, Long userId);
    List<Prediction> findAllByMatchWeekIdOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(Long id);
    List<Prediction> findAllByUserIdOrderByMatchLocalDateTimeDesc(Long id);
    List<Prediction> findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(Long userId, Long id);

    @Transactional
    @Modifying
    @Query("update Prediction p set p.homeTeamScore = :homeTeamScore, p.awayTeamScore = :awayTeamScore " +
            "where p.match.id = :matchId and p.user.id = :userId")
    void update(Long matchId, Long userId, Integer homeTeamScore, Integer awayTeamScore);

}

