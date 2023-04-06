package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PredictionDto;

import java.util.List;

public interface PredictionService {
    PredictionDto save(PredictionDto dto);
    PredictionDto findById(Long id);
    List<PredictionDto> findAllByWeekId(Long id);
    List<PredictionDto> findAllByMatchId(Long id);
    List<PredictionDto> findAllByUserId(Long id);
    List<PredictionDto> findAllByUserIdAndWeekId(Long userId, Long weekId);
    void deleteById(Long id);
    boolean isExist(Long userId, Long matchId);
}
