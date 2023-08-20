package zhigalin.predictions.service.predict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionRepository;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class PredictionService {
    private final PredictionRepository repository;

    public void save(Prediction prediction) {
        Prediction predictionFromDB = repository.findByMatchIdAndUserId(
                prediction.getMatch().getId(), prediction.getUser().getId()
        );
        if (predictionFromDB != null) {
            repository.update(prediction.getMatch().getId(), prediction.getUser().getId(), prediction.getHomeTeamScore(), prediction.getAwayTeamScore());
        }
        repository.save(prediction);
    }

    public Prediction findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Prediction> findAllByWeekId(Long wid) {
        return repository.findAllByMatchWeekIdOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(wid);
    }

    public List<Prediction> findAllByUserId(Long id) {
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(id);
    }

    public List<Prediction> findAllByUserIdAndWeekId(Long userId, Long wid) {
        return repository.findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(userId, wid);
    }

    public void deleteById(Long id) {
        repository.delete(repository.findById(id).orElseThrow());
    }

    public void updatePoints(Long matchId, Long userId) {
        Prediction prediction = repository.findByMatchIdAndUserId(matchId, userId);
        long points;
        Integer realHomeScore = prediction.getMatch().getHomeTeamScore();
        Integer realAwayScore = prediction.getMatch().getAwayTeamScore();
        Integer predictHomeScore = prediction.getHomeTeamScore();
        Integer predictAwayScore = prediction.getAwayTeamScore();
        if (predictHomeScore == null || predictAwayScore == null) {
            points = -1;
        } else {
            points = realHomeScore == null || realAwayScore == null ? 0
                    : realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore) ? 4
                    : realHomeScore - realAwayScore == predictHomeScore - predictAwayScore ? 2
                    : realHomeScore > realAwayScore && predictHomeScore > predictAwayScore ? 1
                    : realHomeScore < realAwayScore && predictHomeScore < predictAwayScore ? 1 : -1;
        }
        repository.updatePoints(matchId, userId, points);
    }

    public boolean isExist(Long userId, Long matchId) {
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(userId)
                .stream()
                .anyMatch(prediction -> prediction.getMatch().getId().equals(matchId));
    }
}
