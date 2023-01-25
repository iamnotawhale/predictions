package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;

import java.util.List;
import java.util.Map;

public interface PredictionService {

    PredictionDto save(PredictionDto dto);

    PredictionDto findById(Long id);

    PredictionDto findByMatchAndUserIds(Long matchId, Long userId);

    List<PredictionDto> findAllByWeekId(Long id);

    List<PredictionDto> findAllByMatchId(Long id);

    List<PredictionDto> findAllByUserId(Long id);

    List<PredictionDto> findAllByUserIdAndWeekId(Long userId, Long weekId);

    Integer getUsersPointsByUserId(Long id);

    Integer getWeeklyUsersPoints(Long userId, Long weekId);

    Map<UserDto, Integer> getAllUsersPoints();

    Map<UserDto, Integer> getUsersPointsByWeek(Long weekId);

    void delete(PredictionDto dto);

    void deleteById(Long id);

    boolean isExist(Long userId, Long matchId);
}
