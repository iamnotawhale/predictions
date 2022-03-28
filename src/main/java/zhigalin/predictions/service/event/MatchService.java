package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface MatchService {
    MatchDto save(MatchDto matchDto);

    MatchDto getById(Long id);

    List<MatchDto> getAllByTodayDate();

    List<MatchDto> getAllByNearestDays(Integer days);

    List<MatchDto> getAllByWeekId(Long id);

    List<MatchDto> getAllByCurrentWeek(Boolean b);

    MatchDto getByTeamIds(Long homeTeamId, Long awayTeamId);

    MatchDto getByTeamNames(String homeTeamName, String awayTeamName);

    MatchDto getByTeamCodes(String home, String away);

    List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName);
}
