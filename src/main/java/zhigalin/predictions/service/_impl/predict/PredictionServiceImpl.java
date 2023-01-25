package zhigalin.predictions.service._impl.predict;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@Service
public class PredictionServiceImpl implements PredictionService {

    private final PredictionRepository repository;
    private final PredictionMapper mapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public PredictionDto save(PredictionDto dto) {
        Prediction prediction = repository.findByMatchIdAndUserId(dto.getMatch().getId(), dto.getUser().getId());
        repository.updateSequence();
        if (prediction != null) {
            mapper.updateEntityFromDto(dto, prediction);
            return mapper.toDto(repository.save(prediction));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public PredictionDto findById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public PredictionDto findByMatchAndUserIds(Long matchId, Long userId) {
        Prediction prediction = repository.findByMatchIdAndUserId(matchId, userId);
        if (prediction != null) {
            return mapper.toDto(prediction);
        }
        return null;
    }

    @Override
    public List<PredictionDto> findAllByWeekId(Long id) {
        return repository.findAllByMatchWeekIdOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByMatchId(Long id) {
        return repository.findAllByMatchId(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByUserId(Long id) {
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByUserIdAndWeekId(Long userId, Long weekId) {
        return repository.findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(userId, weekId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public void delete(PredictionDto dto) {
        repository.delete(mapper.toEntity(dto));
    }

    @Override
    public void deleteById(Long id) {
        repository.delete(repository.findById(id).get());
    }

    @Override
    public Integer getUsersPointsByUserId(Long id) {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .filter(p -> p.getUser().getId().equals(id))
                .mapToInt(PredictionDto::getPoints)
                .sum();
    }

    @Override
    public Integer getWeeklyUsersPoints(Long userId, Long weekId) {
        return repository.findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(userId, weekId)
                .stream()
                .map(mapper::toDto)
                .mapToInt(PredictionDto::getPoints).sum();
    }

    @Override
    public Map<UserDto, Integer> getAllUsersPoints() {
        Map<UserDto, Integer> map = new HashMap<>();
        Iterable<User> allUsers = userRepository.findAll();
        List<PredictionDto> predictionDto = repository.findAll().stream().map(mapper::toDto).toList();
        for (User user : allUsers) {
            int sum = predictionDto
                    .stream()
                    .filter(d -> d.getUser().equals(user))
                    .mapToInt(PredictionDto::getPoints)
                    .sum();
            map.put(userMapper.toDto(user), sum);
        }
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public Map<UserDto, Integer> getUsersPointsByWeek(Long weekId) {
        Map<UserDto, Integer> map = new HashMap<>();
        Iterable<User> all = userRepository.findAll();
        List<PredictionDto> allByWeek = repository.findAllByMatchWeekId(weekId).stream().map(mapper::toDto).toList();
        for (User user : all) {
            int sum = allByWeek.stream().filter(p -> p.getUser().equals(user)).mapToInt(PredictionDto::getPoints).sum();
            map.put(userMapper.toDto(user), sum);
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public boolean isExist(Long userId, Long matchId) {
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(userId)
                .stream()
                .map(mapper::toDto)
                .anyMatch(predictionDto -> predictionDto.getMatch().getId().equals(matchId));
    }
}
