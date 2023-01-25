package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.TeamDto;

import java.util.List;


public interface TeamService {

    TeamDto save(TeamDto teamDto);

    TeamDto findById(Long teamId);

    TeamDto findByName(String teamName);

    TeamDto findByCode(String teamCode);

    TeamDto findByPublicId(Long publicId);

    List<TeamDto> findAll();
}
