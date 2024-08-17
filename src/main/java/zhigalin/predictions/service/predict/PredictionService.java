package zhigalin.predictions.service.predict;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.event.MatchService;

@Slf4j
@Service
public class PredictionService {

    private final PredictionDao predictionDao;
    private final MatchService matchService;

    public PredictionService(PredictionDao predictionDao, MatchService matchService) {
        this.predictionDao = predictionDao;
        this.matchService = matchService;
    }


    public void save(Prediction prediction) {
        predictionDao.save(prediction);
    }

    public Prediction findByMatchIdAndUserId(int matchId, int userId) {
        return predictionDao.findByMatchIdAndUserId(matchId, userId);
    }

    public List<Prediction> findAllByWeekId(int wid) {
        List<Integer> matchIds = matchService.findAllByWeekId(wid).stream().map(Match::getPublicId).toList();
        return predictionDao.findAllByMatchIds(matchIds);
    }

    public List<Prediction> findAllByUserId(int userId) {
        return predictionDao.findAllByUserId(userId);
    }

    public List<Prediction> findAllByUserIdAndWeekId(int userId, int wid) {
        return findAllByWeekId(wid).stream().filter(prediction -> prediction.getUserId() == userId).toList();
    }

    public void deleteById(int userId, int matchPublicId) {
        predictionDao.delete(userId, matchPublicId);
    }

    public void updatePoints(int matchId, int userId) {
        Prediction prediction = predictionDao.findByMatchIdAndUserId(matchId, userId);
        if (prediction == null) {
            return;
        }
        Match match = matchService.findByPublicId(prediction.getMatchPublicId());
        int points;
        Integer realHomeScore = match.getHomeTeamScore();
        Integer realAwayScore = match.getAwayTeamScore();
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
        predictionDao.updatePoints(matchId, userId, points);
    }

    public boolean isExist(int userId, int matchId) {
        return findAllByUserId(userId)
                .stream()
                .anyMatch(prediction -> prediction.getMatchPublicId() == matchId);
    }

    public List<Prediction> getByMatchPublicId(int publicId) {
        return predictionDao.findAllByMatchIds(List.of(publicId));
    }

    public List<Prediction> getAllByMatches(List<Match> matches) {
        return predictionDao.getAllByMatches(matches);
    }
}
