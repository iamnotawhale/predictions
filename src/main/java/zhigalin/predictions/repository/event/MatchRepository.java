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
    List<Match> getAllByWeekIdOrderByMatchDateAscMatchTimeAsc(Long id);

    List<Match> getAllByWeek_IsCurrentOrderByMatchDateAscMatchTimeAsc(Boolean b);

    List<Match> getAllByMatchDateOrderByWeekAscMatchDateAscMatchTimeAsc(LocalDate date);

    List<Match> getAllByLocalDateTimeBetween(LocalDateTime nowMinus2Hour, LocalDateTime now);

    Match getMatchByHomeTeam_IdAndAwayTeam_Id(Long homeTeamId, Long awayTeamId);

    Match getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(String homeTeamName, String awayTeamName);

    Match getMatchByHomeTeam_CodeAndAwayTeam_Code(String home, String away);

    @Query("select m from Match m where m.homeTeam.id = :teamId or m.awayTeam.id = :teamId order by m.localDateTime")
    List<Match> getAllByTeamId(@Param("teamId")Long teamId);

    Match getMatchByPublicId(Long publicId);
}
