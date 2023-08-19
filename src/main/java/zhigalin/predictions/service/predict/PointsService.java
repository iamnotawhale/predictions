package zhigalin.predictions.service.predict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PointsRepository;
import zhigalin.predictions.service.user.UserService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsService {
    private final PointsRepository pointsRepository;
    private final UserService userService;

    public void save(Points points) {
        Points pointsFromDB = pointsRepository.getPointsByUserId(points.getUserId());
        if (pointsFromDB != null) {
            pointsRepository.update(points.getUserId(), points.getValue());
        } else {
            pointsRepository.save(points);
        }
    }

    public Map<User, Long> getAll() {
        updatePoints();
        List<Points> pointsList = pointsRepository.findAll();
        Map<User, Long> map = new LinkedHashMap<>();
        for (Points points : pointsList) {
            map.put(userService.findById(points.getUserId()), points.getValue());
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Map<User, Long> getWeeklyUsersPoints(Long weekId) {
        Map<User, Long> map = new HashMap<>();
        List<User> allUsers = userService.findAll();
        for (User user : allUsers) {
            Long points = pointsRepository.getPointsByUserIdAndMatchWeekId(user.getId(), weekId);
            map.put(user, points != null ? points : 0);
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public void updatePoints() {
        List<User> all = userService.findAll();
        for (User user : all) {
            Long pointsFromDB = pointsRepository.getAllPointsByUserId(user.getId());
            Points points = Points.builder().userId(user.getId()).value(pointsFromDB != null ? pointsFromDB : 0).build();
            save(points);
        }
    }
}
