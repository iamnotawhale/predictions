package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;

import java.util.List;
import java.util.Map;

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

    Map<UserDto, Integer> allUsersPoints();

    boolean isExist(Long userId, Long matchId);

    Map<UserDto, Integer> usersPointsByWeek(Long weekId);
}
