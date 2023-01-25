package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAllByWeekId(Long weekId);

    List<Match> findAllByWeekIsCurrentTrueOrderByLocalDateTime();

    List<Match> findAllByMatchDateOrderByWeekAscLocalDateTimeAsc(LocalDate date);

    List<Match> findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime from, LocalDateTime to);

    Match findMatchByHomeTeamIdAndAwayTeamId(Long homeTeamId, Long awayTeamId);

    Match findMatchByHomeTeamTeamNameAndAwayTeamTeamName(String homeTeamName, String awayTeamName);

    Match findMatchByHomeTeamCodeAndAwayTeamCode(String homeCode, String awayCode);

    @Query("select m from Match m where m.homeTeam.id = :teamId or m.awayTeam.id = :teamId order by m.localDateTime")
    List<Match> findAllByTeamId(@Param("teamId") Long teamId);

    Match findMatchByPublicId(Long publicId);

    List<Match> findAllByStatus(String status);
}
