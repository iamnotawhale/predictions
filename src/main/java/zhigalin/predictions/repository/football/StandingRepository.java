package zhigalin.predictions.repository.football;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.football.Standing;

import javax.transaction.Transactional;

@Repository
public interface StandingRepository extends JpaRepository<Standing, Long> {
    Standing findByTeamId(Long id);
    Standing findByTeamPublicId(Long publicId);

    @Transactional
    @Modifying
    @Query("update Standing s set s.games = :games, s.points = :points, s.won = :won, s.draw = :draw, s.lost = :lost, " +
            "s.goalsScored = :goalsScored, s.goalsAgainst = :goalsAgainst where s.team.id = :id")
    void update(Long id, Integer games, Integer points, Integer won, Integer draw, Integer lost, Integer goalsScored, Integer goalsAgainst);
}
