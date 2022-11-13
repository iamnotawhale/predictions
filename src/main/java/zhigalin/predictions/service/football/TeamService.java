package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.TeamDto;


public interface TeamService {

    TeamDto saveTeam(TeamDto teamDto);

    TeamDto getById(Long id);

    TeamDto getByName(String teamName);

    TeamDto getByCode(String code);

    TeamDto getByPublicId(Long id);
}
