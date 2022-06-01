package zhigalin.predictions.repository.event;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.AggregateStats;
import zhigalin.predictions.model.event.Stats;

@Repository
public interface StatsRepository extends CrudRepository<Stats, Long> {
    Stats getByMatchPublicIdAndTeam_Id(Long matchPublicId, Long teamId);

    @Query("select new zhigalin.predictions.model.event.AggregateStats(s.team.id, avg(s.possessionPercent), avg(s.shots), " +
            "avg(s.shotsOnTarget), avg(s.shotsOffTarget), avg(s.shotsBlocked), avg(s.corners), avg(s.offsides), " +
            "avg(s.passes), avg(s.passesAccurate), avg(s.passPercent), avg(s.insideBoxShots), avg(s.outsideBoxShots), " +
            "avg(s.fouls), avg(s.ballSafe), avg(s.yellowCards), avg(s.redCards)) from Stats s where s.team.id=:id group by s.team.id")
    AggregateStats getAvgStatsByTeamId(@Param("id") Long id);
}
