package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface HeadToHeadService {
    HeadToHeadDto save(HeadToHeadDto dto);
    List<HeadToHeadDto> findAllByTwoTeamsCode(String homeTeamCode, String awayTeamCode);
    List<HeadToHeadDto> findAllByMatch(MatchDto dto);
    List<List<HeadToHeadDto>> findAllByCurrentWeek();
}
