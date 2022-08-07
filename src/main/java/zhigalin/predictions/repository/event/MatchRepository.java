package zhigalin.predictions.repository.event;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends CrudRepository<Match, Long> {
    List<Match> getAllByWeekIdOrderByMatchDateAscMatchTimeAsc(Long id);

    List<Match> getAllByWeek_IsCurrentOrderByMatchDateAscMatchTimeAsc(Boolean b);

    List<Match> getAllByMatchDateOrderByWeekAscMatchDateAscMatchTimeAsc(LocalDate date);

    List<Match> getAllByHomeTeam_IdOrAwayTeam_IdOrderByLocalDateTime(Long hId, Long aId);

    List<Match> getAllByLocalDateTimeBetween(LocalDateTime nowMinus2Hour, LocalDateTime now);

    List<Match> getTop5ByHomeTeam_IdAndResultNotNullOrAwayTeam_IdAndResultNotNullOrderByLocalDateTimeDesc(Long hId, Long aId);

    Match getMatchByHomeTeam_IdAndAwayTeam_Id(Long homeTeamId, Long awayTeamId);

    Match getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(String homeTeamName, String awayTeamName);

    Match getMatchByHomeTeam_CodeAndAwayTeam_Code(String home, String away);

    Match getMatchByHomeTeam_IdAndAwayTeam_IdAndResult(Long homeTeamId, Long awayTeamId, String result);

    Match getMatchByPublicId(Long publicId);
}
