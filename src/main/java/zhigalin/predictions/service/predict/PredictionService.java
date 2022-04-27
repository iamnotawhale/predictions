package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PredictionDto;

import java.util.List;

public interface PredictionService {
    List<PredictionDto> getAllByWeekId(Long id);

    PredictionDto getById(Long id);

    List<PredictionDto> getAllByMatchId(Long id);

    PredictionDto save(PredictionDto dto);

    PredictionDto getByMatchAndUserIds(Long matchId, Long userId);

    List<PredictionDto> getAllByUser_Id(Long id);

    List<PredictionDto> getAllByUserIdAndWeekId(Long userId, Long weekId);

    Integer getUsersPointsByUserId(Long id);

    Integer getWeeklyUsersPoints(Long userId, Long weekId);

    void deleteById(Long id);

    void delete(PredictionDto dto);

    PredictionDto updatePoints(PredictionDto dto);
}
