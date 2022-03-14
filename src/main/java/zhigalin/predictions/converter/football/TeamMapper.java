package zhigalin.predictions.converter.football;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.football.Team;

@Mapper(componentModel = "spring")
public interface TeamMapper extends CustomMapper<Team, TeamDto> {
}
