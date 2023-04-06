package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.TeamDto;

import java.util.List;


public interface TeamService {
    TeamDto save(TeamDto dto);
    TeamDto findById(Long id);
    TeamDto findByName(String name);
    TeamDto findByCode(String code);
    TeamDto findByPublicId(Long publicId);
    List<TeamDto> findAll();
}
