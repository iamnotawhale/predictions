package zhigalin.predictions.converter.predict;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.predict.OddsDto;
import zhigalin.predictions.model.predict.Odds;

@Mapper(componentModel = "spring")
public interface OddsMapper extends CustomMapper<Odds, OddsDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(OddsDto dto, @MappingTarget Odds entity);
}
