package zhigalin.predictions.repository.predict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Prediction;

import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    Prediction findByMatchIdAndUserIdAndSeasonIsCurrent(Long matchId, Long userId, boolean current);
    List<Prediction> findAllByMatchWeekWidAndMatchWeekSeasonIsCurrentTrueOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(Long wid);
    List<Prediction> findAllByUserIdAndSeasonIsCurrentTrueOrderByMatchLocalDateTimeDesc(Long id);
    List<Prediction> findAllByUserIdAndMatchWeekWidAndMatchWeekSeasonIsCurrentTrueOrderByMatchLocalDateTime(Long userId, Long wid);
    @Query(value = "SELECT setval('predict_sequence', (SELECT MAX(id) FROM predict) + 1, false)", nativeQuery = true)
    void updateSequence();
}

