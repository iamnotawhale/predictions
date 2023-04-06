package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface MatchService {
    MatchDto save(MatchDto dto);
    MatchDto findById(Long id);
    MatchDto findByPublicId(Long publicId);
    MatchDto findByTeamNames(String homeTeamName, String awayTeamName);
    MatchDto findByTeamCodes(String homeTeamCode, String awayTeamCode);
    List<MatchDto> findAll();
    List<MatchDto> findAllByTodayDate();
    List<MatchDto> findAllByTeamId(Long id);
    List<MatchDto> findAllByUpcomingDays(Integer days);
    List<MatchDto> findAllByWeekId(Long weekId);
    List<MatchDto> findAllByStatus(String status);
    List<MatchDto> findAllByCurrentWeek();
    List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName);
    List<MatchDto> findLast5MatchesByTeamId(Long id);
    List<String> getLast5MatchesResultByTeamId(Long id);
    List<MatchDto> findOnline();
    MatchDto getOnlineResult(String teamName);
}
