package zhigalin.predictions.converter.event;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.Match;

@Mapper(componentModel = "spring")
public interface MatchMapper extends CustomMapper<Match, MatchDto> {
}
