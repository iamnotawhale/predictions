package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.HeadToHeadDto;

import java.util.List;

public interface HeadToHeadService {
    HeadToHeadDto save(HeadToHeadDto headToHeadDto);
    List<HeadToHeadDto> getAllByTwoTeamsId(Long firstTeam, Long secondTeam);
    List<HeadToHeadDto> getAllByTwoTeamsCode(String firstTeamCode, String secondTeamCode);
}
