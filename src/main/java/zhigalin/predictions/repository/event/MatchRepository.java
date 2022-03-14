package zhigalin.predictions.repository.event;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;

import java.util.List;

@Repository
public interface MatchRepository extends CrudRepository<Match, Long> {
    List<Match> getAllByWeekIdOrderByMatchDate(Long id);

    Match getMatchByHomeTeam_IdAndAwayTeam_Id(Long homeTeamId, Long awayTeamId);

    Match getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(String homeTeamName, String awayTeamName);

    Match getMatchByHomeTeam_CodeAndAwayTeam_Code(String home, String away);
}
