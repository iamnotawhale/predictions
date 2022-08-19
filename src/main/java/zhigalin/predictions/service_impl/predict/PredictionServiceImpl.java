package zhigalin.predictions.service_impl.predict;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PredictionRepository;
import zhigalin.predictions.repository.user.UserRepository;
import zhigalin.predictions.service.predict.PredictionService;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final PredictionRepository repository;
    private final PredictionMapper mapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Autowired
    public PredictionServiceImpl(PredictionRepository repository, UserRepository userRepository, PredictionMapper mapper, UserMapper userMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<PredictionDto> getAllByWeekId(Long id) {
        List<Prediction> list = repository.getAllByMatch_Week_IdOrderByMatch_LocalDateTimeDescMatch_HomeTeam_IdAsc(id);
        return list.stream().map(mapper::toDto).map(this::updatePoints).toList();
    }

    @Override
    public PredictionDto getById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public List<PredictionDto> getAllByMatchId(Long id) {
        List<Prediction> list = repository.getAllByMatch_Id(id);
        return list.stream().map(mapper::toDto).map(this::updatePoints).toList();
    }

    @Override
    public PredictionDto updatePoints(PredictionDto dto) {
        dto.setPoints(getPoints(dto));
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public Map<UserDto, Integer> allUsersPoints() {
        Map<UserDto, Integer> map = new HashMap<>();
        Iterable<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            map.put(userMapper.toDto(user), getUsersPointsByUserId(user.getId()));
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public boolean isExist(Long userId, Long matchId) {
        List<PredictionDto> list = getAllByUser_Id(userId);
        return list.stream().anyMatch(predictionDto -> predictionDto.getMatch().getId().equals(matchId));
    }

    @Override
    public Map<UserDto, Integer> usersPointsByWeek(Long weekId) {
        Map<UserDto, Integer> map = new HashMap<>();
        Iterable<User> all = userRepository.findAll();
        for (User user : all) {
            List<Prediction> list = repository.getAllByUser_IdAndMatch_Week_IdOrderByMatch_LocalDateTime(user.getId(), weekId);
            int sum = list.stream().mapToInt(Prediction::getPoints).sum();
            map.put(userMapper.toDto(user), sum);
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public PredictionDto save(PredictionDto dto) {
        dto.setPoints(getPoints(dto));
        Prediction prediction = repository.getByMatch_IdAndUser_Id(dto.getMatch().getId(), dto.getUser().getId());
        if (prediction != null) {
            mapper.updateEntityFromDto(dto, prediction);
            return mapper.toDto(repository.save(prediction));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public PredictionDto getByMatchAndUserIds(Long matchId, Long userId) {
        Prediction prediction = repository.getByMatch_IdAndUser_Id(matchId, userId);
        return mapper.toDto(prediction);
    }

    @Override
    public List<PredictionDto> getAllByUser_Id(Long id) {
        List<Prediction> list = repository.getAllByUser_IdOrderByMatch_LocalDateTimeDesc(id);
        return list.stream().map(mapper::toDto).map(this::updatePoints).toList();
    }

    @Override
    public List<PredictionDto> getAllByUserIdAndWeekId(Long userId, Long weekId) {
        List<Prediction> list = repository.getAllByUser_IdAndMatch_Week_IdOrderByMatch_LocalDateTime(userId, weekId);
        return list.stream().map(mapper::toDto).map(this::updatePoints).toList();
    }

    @Override
    public Integer getUsersPointsByUserId(Long id) {
        if (getAllByUser_Id(id).isEmpty()) {
            return 0;
        }
        return repository.getPointsByUser_Id(id);
    }

    @Override
    public Integer getWeeklyUsersPoints(Long userId, Long weekId) {
        List<Prediction> list = repository.getAllByUser_IdAndMatch_Week_IdOrderByMatch_LocalDateTime(userId, weekId);
        Integer points = 0;
        for (Prediction prediction : list) {
            if (prediction.getPoints() == null) {
                points += 0;
            } else {
                points += prediction.getPoints();
            }
        }
        return points;
    }

    @Override
    public void deleteById(Long id) {
        Prediction prediction = repository.findById(id).get();
        repository.delete(prediction);
    }

    @Override
    public void delete(PredictionDto dto) {
        repository.delete(mapper.toEntity(dto));
    }

    public Integer getPoints(PredictionDto dto) {
        Integer realHomeScore = dto.getMatch().getHomeTeamScore();
        Integer realAwayScore = dto.getMatch().getAwayTeamScore();
        Integer predictHomeScore = dto.getHomeTeamScore();
        Integer predictAwayScore = dto.getAwayTeamScore();

        return realHomeScore == null || realAwayScore == null ? 0
                : realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore) ? 5
                : realHomeScore - realAwayScore == predictHomeScore - predictAwayScore ? 3
                : realHomeScore > realAwayScore && predictHomeScore > predictAwayScore ? 1
                : realHomeScore < realAwayScore && predictHomeScore < predictAwayScore ? 1 : -1;
    }
}
