package zhigalin.predictions.converter.football;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.model.football.Standing;

@Mapper(componentModel = "spring")
public interface StandingMapper extends CustomMapper<Standing, StandingDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(StandingDto dto, @MappingTarget Standing entity);
}
