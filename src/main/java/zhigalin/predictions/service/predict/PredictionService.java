package zhigalin.predictions.service.predict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionRepository;
import zhigalin.predictions.util.FieldsUpdater;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class PredictionService {
    private final PredictionRepository repository;
    public Prediction save(Prediction prediction) {
        Prediction predictionFromDB = repository.findByMatchIdAndUserIdAndSeasonIsCurrent(
                prediction.getMatch().getId(), prediction.getUser().getId(), true
        );
        repository.updateSequence();
        if (predictionFromDB != null) {
            return repository.save(FieldsUpdater.update(predictionFromDB, prediction));
        }
        return repository.save(prediction);
    }

    public Prediction findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Prediction> findAllByWeekId(Long wid) {
        return repository.findAllByMatchWeekWidAndMatchWeekSeasonIsCurrentTrueOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(wid);
    }

    public List<Prediction> findAllByUserId(Long id) {
        return repository.findAllByUserIdAndSeasonIsCurrentTrueOrderByMatchLocalDateTimeDesc(id);
    }

    public List<Prediction> findAllByUserIdAndWeekId(Long userId, Long wid) {
        return repository.findAllByUserIdAndMatchWeekWidAndMatchWeekSeasonIsCurrentTrueOrderByMatchLocalDateTime(userId, wid);
    }

    public void deleteById(Long id) {
        repository.delete(repository.findById(id).orElseThrow());
    }

    public boolean isExist(Long userId, Long matchId) {
        return repository.findAllByUserIdAndSeasonIsCurrentTrueOrderByMatchLocalDateTimeDesc(userId)
                .stream()
                .anyMatch(prediction -> prediction.getMatch().getId().equals(matchId));
    }
}
