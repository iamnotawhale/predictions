package zhigalin.predictions.converter.predict;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.predict.PointsDto;
import zhigalin.predictions.model.predict.Points;

@Mapper(componentModel = "spring")
public interface PointsMapper extends CustomMapper<Points, PointsDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(PointsDto dto, @MappingTarget Points entity);
}
