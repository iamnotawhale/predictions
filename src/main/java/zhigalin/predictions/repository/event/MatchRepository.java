package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    Match findByHomeTeamIdAndAwayTeamId(Long homeTeamId, Long awayTeamId);
    Match findByHomeTeamCodeAndAwayTeamCode(String homeTeamCode, String awayTeamCode);
    Match findByHomeTeamNameAndAwayTeamName(String homeTeamName, String awayTeamName);
    Match findByPublicId(Long publicId);
    List<Match> findAllByWeekIdOrderByLocalDateTime(Long weekId);
    List<Match> findAllByWeekIsCurrentTrueOrderByLocalDateTime();
    List<Match> findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime from, LocalDateTime to);
    List<Match> findAllByStatus(String status);
    @Query("select m from Match m where m.homeTeam.id = :id or m.awayTeam.id = :id order by m.localDateTime")
    List<Match> findAllByTeamId(@Param("id") Long id);
}
