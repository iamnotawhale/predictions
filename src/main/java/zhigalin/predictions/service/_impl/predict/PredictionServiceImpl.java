package zhigalin.predictions.service._impl.predict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionRepository;
import zhigalin.predictions.service.predict.PredictionService;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class PredictionServiceImpl implements PredictionService {
    private final PredictionRepository repository;
    private final PredictionMapper mapper;

    @Override
    public PredictionDto save(PredictionDto dto) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
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
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public List<PredictionDto> findAllByWeekId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByMatchWeekIdOrderByMatchLocalDateTimeDescMatchHomeTeamIdAsc(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByMatchId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByMatchId(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByUserId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<PredictionDto> findAllByUserIdAndWeekId(Long userId, Long weekId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByUserIdAndMatchWeekIdOrderByMatchLocalDateTime(userId, weekId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        repository.delete(repository.findById(id).get());
    }

    @Override
    public boolean isExist(Long userId, Long matchId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByUserIdOrderByMatchLocalDateTimeDesc(userId)
                .stream()
                .map(mapper::toDto)
                .anyMatch(predictionDto -> predictionDto.getMatch().getId().equals(matchId));
    }
}
