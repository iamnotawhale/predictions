package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.PredictionDto;

import java.util.List;

public interface PredictionService {
    List<PredictionDto> getAllByWeekId(Long id);

    PredictionDto getById(Long id);

    List<PredictionDto> getAllByMatchId(Long id);

    PredictionDto save(PredictionDto dto);
}
