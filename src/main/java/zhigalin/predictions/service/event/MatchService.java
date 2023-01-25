package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.MatchDto;

import java.util.List;

public interface MatchService {

    MatchDto save(MatchDto matchDto);

    MatchDto findById(Long matchId);

    MatchDto findByPublicId(Long publicId);

    MatchDto findByTeamIds(Long homeTeamId, Long awayTeamId);

    MatchDto findByTeamNames(String homeTeamName, String awayTeamName);

    MatchDto findByTeamCodes(String homeCode, String awayCode);

    List<MatchDto> findAll();

    List<MatchDto> findAllByTodayDate();

    List<MatchDto> findAllByTeamId(Long teamId);

    List<MatchDto> findAllByUpcomingDays(Integer days);

    List<MatchDto> findAllByWeekId(Long weekId);

    List<MatchDto> findAllByStatus(String status);

    List<MatchDto> findAllByCurrentWeek();

    List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName);

    List<MatchDto> findLast5MatchesByTeamId(Long teamId);

    List<String> getLast5MatchesResultByTeamId(Long teamId);

    List<MatchDto> findOnline();

    MatchDto getOnlineResult(String teamName);
}
