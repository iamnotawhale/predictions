package zhigalin.predictions.repository.predict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Prediction;

import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Prediction findByMatchIdAndUserId(Long matchId, Long userId);

    List<Prediction> findAllByMatchId(Long id);

    List<Prediction> findAllByMatchWeekId(Long id);

    List<Prediction> findAllByMatchWeekIdOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(Long id);

    List<Prediction> findAllByUserIdOrderByMatchLocalDateTimeDesc(Long id);

    List<Prediction> findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(Long userId, Long weekId);

    @Query(value = "SELECT setval('predict_sequence', (SELECT MAX(id) FROM predict) + 1, false)", nativeQuery = true)
    void updateSequence();
}

