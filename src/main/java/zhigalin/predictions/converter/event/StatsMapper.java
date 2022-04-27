package zhigalin.predictions.converter.event;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.StatsDto;
import zhigalin.predictions.model.event.Stats;

@Mapper(componentModel = "spring")
public interface StatsMapper extends CustomMapper<Stats, StatsDto> {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(StatsDto dto, @MappingTarget Stats entity);
}
