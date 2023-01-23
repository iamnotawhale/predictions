package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.TeamDto;

import java.util.List;


public interface TeamService {

    List<TeamDto> findAll();

    TeamDto saveTeam(TeamDto teamDto);

    TeamDto getById(Long id);

    TeamDto getByName(String teamName);

    TeamDto getByCode(String code);

    TeamDto getByPublicId(Long id);
}
