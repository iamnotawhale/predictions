package zhigalin.predictions.repository.event;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.HeadToHead;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HeadToHeadRepository extends CrudRepository<HeadToHead, Long> {
    HeadToHead getByHomeTeam_PublicIdAndAwayTeam_PublicIdAndLocalDateTime(Long homePublicId, Long awayPublicId, LocalDateTime localDateTime);
    List<HeadToHead> getAllByHomeTeam_IdAndAwayTeam_Id(Long homeTeamId, Long awayTeamId);
    List<HeadToHead> getAllByHomeTeam_CodeAndAwayTeam_Code(String firstTeamCode, String secondTeamCode);
}
