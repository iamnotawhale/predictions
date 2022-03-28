package zhigalin.predictions.converter.football;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.model.football.Standing;

@Mapper(componentModel = "spring")
public interface StandingMapper extends CustomMapper<Standing, StandingDto> {
}
