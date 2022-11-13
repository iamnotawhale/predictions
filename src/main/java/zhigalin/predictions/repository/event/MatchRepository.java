package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends CrudRepository<Match, Long> {

    List<Match> getAllByWeek_IdOrderByLocalDateTime(Long id);

    List<Match> getAllByWeek_IsCurrentOrderByLocalDateTime(Boolean b);

    List<Match> getAllByMatchDateOrderByWeekAscLocalDateTimeAsc(LocalDate date);

    List<Match> getAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime from, LocalDateTime to);

    Match getMatchByHomeTeam_IdAndAwayTeam_Id(Long homeTeamId, Long awayTeamId);

    Match getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(String homeTeamName, String awayTeamName);

    Match getMatchByHomeTeam_CodeAndAwayTeam_Code(String home, String away);

    @Query("select m from Match m where m.homeTeam.id = :teamId or m.awayTeam.id = :teamId order by m.localDateTime")
    List<Match> getAllByTeamId(@Param("teamId") Long teamId);

    Match getMatchByPublicId(Long publicId);

    List<Match> getAllByStatus(String status);
}
