package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.HeadToHead;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HeadToHeadRepository extends JpaRepository<HeadToHead, Long> {

    HeadToHead findByHomeTeamPublicIdAndAwayTeamPublicIdAndLocalDateTime(Long homePublicId, Long awayPublicId, LocalDateTime localDateTime);

    List<HeadToHead> findAllByHomeTeamCodeAndAwayTeamCode(String homeTeamCode, String awayTeamCode);
}
