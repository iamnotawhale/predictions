package zhigalin.predictions.converter.event;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.model.event.HeadToHead;

@Mapper(componentModel = "spring")
public interface HeadToHeadMapper extends CustomMapper<HeadToHead, HeadToHeadDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(HeadToHeadDto dto, @MappingTarget HeadToHead entity);
}
