package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface MatchService {
    MatchDto save(MatchDto matchDto);

    MatchDto getById(Long id);

    MatchDto getByPublicId(Long publicId);

    List<MatchDto> getAllByTodayDate();

    List<MatchDto> getAllByUpcomingDays(Integer days);

    List<MatchDto> getAllByWeekId(Long id);

    List<MatchDto> getAllByCurrentWeek(Boolean b);

    List<MatchDto> getAll();

    MatchDto getByTeamIds(Long homeTeamId, Long awayTeamId);

    MatchDto getByTeamNames(String homeTeamName, String awayTeamName);

    MatchDto getByTeamCodes(String home, String away);

    List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName);

    List<MatchDto> getAllByTeamId(Long id);

    List<MatchDto> getLast5MatchesByTeamId(Long id);

    List<String> getLast5MatchesResultByTeamId(Long id);

    List<MatchDto> getOnline();

    List<MatchDto> getAllCompletedAndCurrent();
}
