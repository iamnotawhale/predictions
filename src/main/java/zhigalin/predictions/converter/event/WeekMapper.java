package zhigalin.predictions.converter.event;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.model.event.Week;

@Mapper(componentModel = "spring")
public interface WeekMapper extends CustomMapper<Week, WeekDto> {
}
