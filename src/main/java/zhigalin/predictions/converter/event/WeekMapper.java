package zhigalin.predictions.converter.event;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.model.event.Week;

@Mapper(componentModel = "spring")
public interface WeekMapper extends CustomMapper<Week, WeekDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(WeekDto dto, @MappingTarget Week entity);
}
