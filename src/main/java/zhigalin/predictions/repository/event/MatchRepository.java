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
    Match findByHomeTeamIdAndAwayTeamIdAndLocalDateTime(Long homeTeamId, Long awayTeamId, LocalDateTime localDateTime);
    Match findByHomeTeamCodeAndAwayTeamCodeAndWeekSeasonIsCurrentTrue(String homeTeamCode, String awayTeamCode);
    Match findByHomeTeamNameAndAwayTeamNameAndWeekSeasonIsCurrentTrue(String homeTeamName, String awayTeamName);
    Match findByPublicId(Long publicId);
    List<Match> findAllByWeekSeasonIsCurrentTrue();
    List<Match> findAllByWeekWidAndWeekSeasonIsCurrentTrueOrderByLocalDateTime(Long wid);
    List<Match> findAllByWeekIsCurrentTrueOrderByLocalDateTime();
    List<Match> findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime from, LocalDateTime to);
    List<Match> findAllByStatusAndWeekSeasonIsCurrentTrue(String status);
    @Query("select m " +
            "from Match m " +
            "where (m.homeTeam.id = :id or m.awayTeam.id = :id) " +
            "and m.week.season.isCurrent = true " +
            "order by m.localDateTime")
    List<Match> findAllByTeamId(@Param("id") Long id);
    @Query(value = "SELECT setval('match_sequence', (SELECT MAX(id) FROM match) + 1, false)", nativeQuery = true)
    void updateSequence();
}
