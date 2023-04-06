package zhigalin.predictions.converter.predict;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;

@Mapper(componentModel = "spring")
public interface PredictionMapper extends CustomMapper<Prediction, PredictionDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(PredictionDto dto, @MappingTarget Prediction entity);
}
