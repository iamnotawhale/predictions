package zhigalin.predictions.service_impl.predict;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionRepository;
import zhigalin.predictions.service.predict.PredictionService;

import java.util.List;

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
        List<Prediction> list = repository.getAllByMatch_Week_IdOrderByMatch_LocalDateTimeAscMatch_HomeTeam_IdAsc(id);
        return list.stream().map(mapper::toDto).map(this::updatePoints).toList();
    }

    @Override
    public PredictionDto getById(Long id) {
        return mapper.toDto(repository.findById(id).get());
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
        List<Prediction> list = repository.getAllByUser_IdOrderByMatch_LocalDateTimeDesc(id);
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
        Integer points;
        Integer realHomeScore = dto.getMatch().getHomeTeamScore();
        Integer realAwayScore = dto.getMatch().getAwayTeamScore();
        Integer predictHomeScore = dto.getHomeTeamScore();
        Integer predictAwayScore = dto.getAwayTeamScore();
        if (realHomeScore == null || realAwayScore == null) {
            points = null;
        } else if (realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore)) {
            points = 5;
        } else if (realHomeScore - realAwayScore == predictHomeScore - predictAwayScore) {
            points = 3;
        } else if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
            points = 1;
        } else if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
            points = 1;
        } else {
            points = 0;
        }
        return points;
    }
}
