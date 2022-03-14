package zhigalin.predictions.converter.predict;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;

@Mapper(componentModel = "spring")
public interface PredictionMapper extends CustomMapper<Prediction, PredictionDto> {
}
