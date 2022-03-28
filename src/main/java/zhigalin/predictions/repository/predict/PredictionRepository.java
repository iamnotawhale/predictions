package zhigalin.predictions.repository.predict;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Prediction;

import java.util.List;

@Repository
public interface PredictionRepository extends CrudRepository<Prediction, Long> {
   List<Prediction> getAllByMatch_Id(Long id);

   List<Prediction> getAllByMatch_Week_Id(Long id);

   Prediction getByMatch_IdAndUser_Id(Long matchId, Long userId);

   List<Prediction> getAllByUser_IdOrderByMatch_MatchDateAscMatch_MatchTimeAsc(Long id);

   List<Prediction> getAllByUser_IdAndMatch_Week_Id(Long userId, Long weekId);
}
