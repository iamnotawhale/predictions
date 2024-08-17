package zhigalin.predictions.service.predict;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PointsDao;
import zhigalin.predictions.service.user.UserService;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsService {
    private final PointsDao pointsDao;
    private final UserService userService;

    public void save(Points points) {
        Points pointsFromDB = pointsDao.getPointsByUserId(points.getUserId());
        if (pointsFromDB != null) {
            pointsDao.update(points.getUserId(), points.getValue());
        } else {
            pointsDao.save(points);
        }
    }

    public Map<User, Integer> getAll() {
        updatePoints();
        List<Points> pointsList = pointsDao.findAll();
        Map<User, Integer> map = new LinkedHashMap<>();
        for (Points points : pointsList) {
            map.put(userService.findById(points.getUserId()), points.getValue());
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Map<User, Integer> getWeeklyUsersPoints(int weekId) {
        Map<User, Integer> map = new HashMap<>();
        List<User> allUsers = userService.findAll();
        for (User user : allUsers) {
            Integer points = pointsDao.getPointsByUserIdAndMatchWeekId(user.getId(), weekId);
            map.put(user, points != null ? points : 0);
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public void updatePoints() {
        List<User> all = userService.findAll();
        for (User user : all) {
            Integer pointsFromDB = pointsDao.getAllPointsByUserId(user.getId());
            Points points = Points.builder().userId(user.getId()).value(pointsFromDB != null ? pointsFromDB : 0).build();
            save(points);
        }
    }
}
