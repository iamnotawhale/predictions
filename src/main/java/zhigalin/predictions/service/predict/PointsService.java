package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PointsDto;
import zhigalin.predictions.dto.user.UserDto;

import java.util.Map;

public interface PointsService {
    PointsDto save(PointsDto pointsDto);
    Map<UserDto, Long> getAll();
    Map<UserDto, Long> getWeeklyUsersPoints(Long weekId);
}
