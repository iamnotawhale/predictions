package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface HeadToHeadService {

    HeadToHeadDto save(HeadToHeadDto headToHeadDto);

    List<HeadToHeadDto> getAllByTwoTeamsCode(String firstTeamCode, String secondTeamCode);

    List<HeadToHeadDto> getAllByMatch(MatchDto matchDto);
}
