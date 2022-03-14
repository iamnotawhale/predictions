package zhigalin.predictions.converter.event;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.model.event.Season;

@Mapper(componentModel = "spring")
public interface SeasonMapper extends CustomMapper<Season, SeasonDto> {
}
