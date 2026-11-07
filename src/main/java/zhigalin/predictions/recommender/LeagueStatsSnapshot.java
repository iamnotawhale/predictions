package zhigalin.predictions.recommender;

import java.util.List;
import zhigalin.predictions.recommender.model.LeagueAveragesSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

public record LeagueStatsSnapshot(List<TeamStatsSnapshot> teams, LeagueAveragesSnapshot league) {

    public LeagueAveragesSnapshot leagueForWeek(int weekId) {
        return new LeagueAveragesSnapshot(
                weekId,
                league.avgHomeScored(),
                league.avgAwayScored(),
                league.avgHomeConceded(),
                league.avgAwayConceded(),
                league.fetchedAt()
        );
    }
}
