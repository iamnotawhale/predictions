package zhigalin.predictions.service_impl.predict;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionRepository;
import zhigalin.predictions.service.predict.PredictionService;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final PredictionRepository repository;
    private final PredictionMapper mapper;

    @Autowired
    public PredictionServiceImpl(PredictionRepository repository, PredictionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<PredictionDto> getAllByWeekId(Long id) {
        List<Prediction> list = repository.getAllByMatch_Week_Id(id);
        return list.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public PredictionDto getById(Long id) {
        return mapper.toDto(repository.findById(id).get());
    }

    @Override
    public List<PredictionDto> getAllByMatchId(Long id) {
        List<Prediction> list = repository.getAllByMatch_Id(id);
        return list.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public PredictionDto save(PredictionDto dto) {
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
        List<Prediction> list = repository.getAllByUser_IdOrderByMatch_MatchDateAscMatch_MatchTimeAsc(id);
        return list.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<PredictionDto> getAllByUserIdAndWeekId(Long userId, Long weekId) {
        List<Prediction> list = repository.getAllByUser_IdAndMatch_Week_Id(userId, weekId);
        return list.stream().map(mapper::toDto).collect(Collectors.toList());
    }
}
