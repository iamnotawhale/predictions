package zhigalin.predictions.converter.event;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.Match;

@Mapper(componentModel = "spring")
public interface MatchMapper extends CustomMapper<Match, MatchDto> {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(MatchDto dto, @MappingTarget Match entity);
}
