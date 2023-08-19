package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    Match findByHomeTeamCodeAndAwayTeamCode(String homeTeamCode, String awayTeamCode);
    Match findByHomeTeamNameAndAwayTeamName(String homeTeamName, String awayTeamName);
    Match findByPublicId(Long publicId);
    List<Match> findAllByWeekIdOrderByLocalDateTime(Long id);
    List<Match> findAllByWeekIsCurrentTrueOrderByLocalDateTime();
    List<Match> findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime from, LocalDateTime to);
    List<Match> findAllByStatus(String status);
    @Query("select m " +
            "from Match m " +
            "where (m.homeTeam.id = :id or m.awayTeam.id = :id) " +
            "order by m.localDateTime")
    List<Match> findAllByTeamId(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("update Match m " +
            "set m.homeTeamScore = :homeScore, m.awayTeamScore = :awayScore, m.result = :result, m.status = :status " +
            "where m.publicId = :publicId")
    void updateMatch(Long publicId, Integer homeScore, Integer awayScore, String result, String status);

    @Transactional
    @Modifying
    @Query("update Match m " +
            "set m.status = :status, m.localDateTime = :ldt " +
            "where m.publicId = :publicId")
    void updateStatusAndDateTime(Long publicId, String status, LocalDateTime ldt);
}
