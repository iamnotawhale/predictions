package zhigalin.predictions.recommender;

import java.util.List;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

public record LeagueStatsSnapshot(List<FootyStatsTeamSnapshot> teams, FootyStatsLeagueSnapshot league) {

    public FootyStatsLeagueSnapshot leagueForWeek(int weekId) {
        return new FootyStatsLeagueSnapshot(
                weekId,
                league.avgHomeScored(),
                league.avgAwayScored(),
                league.avgHomeConceded(),
                league.avgAwayConceded(),
                league.fetchedAt()
        );
    }
}
