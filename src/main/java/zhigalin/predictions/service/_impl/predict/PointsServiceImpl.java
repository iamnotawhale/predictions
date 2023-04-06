package zhigalin.predictions.service._impl.predict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.PointsMapper;
import zhigalin.predictions.dto.predict.PointsDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.repository.predict.PointsRepository;
import zhigalin.predictions.service.predict.PointsService;
import zhigalin.predictions.service.user.UserService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsServiceImpl implements PointsService {
    private final PointsRepository pointsRepository;
    private final PointsMapper pointsMapper;
    private final UserService userService;

    @Override
    public PointsDto save(PointsDto pointsDto) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Points points = pointsRepository.getPointsByUserId(pointsDto.getUserId());
        if (points != null) {
            pointsMapper.updateEntityFromDto(pointsDto, points);
            return pointsMapper.toDto(points);
        }
        return pointsMapper.toDto(pointsRepository.save(pointsMapper.toEntity(pointsDto)));
    }

    @Override
    public Map<UserDto, Long> getAll() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        updatePoints();
        List<Points> pointsList = pointsRepository.findAll();
        Map<UserDto, Long> map = new LinkedHashMap<>();
        for (Points points : pointsList) {
            map.put(userService.findById(points.getUserId()), points.getValue());
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public Map<UserDto, Long> getWeeklyUsersPoints(Long weekId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Map<UserDto, Long> map = new HashMap<>();
        List<UserDto> allUsers = userService.findAll();
        for (UserDto userDto : allUsers) {
            Long points = pointsRepository.getPointsByUserIdAndMatchWeekId(userDto.getId(), weekId);
            map.put(userDto, points != null ? points : 0);
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public void updatePoints() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        List<UserDto> all = userService.findAll();
        for (UserDto userDto : all) {
            Long points = pointsRepository.getAllPointsByUserId(userDto.getId());
            PointsDto dto = PointsDto.builder().userId(userDto.getId()).value(points != null ? points : 0).build();
            save(dto);
        }
    }
}
