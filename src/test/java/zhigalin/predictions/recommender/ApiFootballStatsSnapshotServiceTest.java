package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService.TeamSeasonExtras;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.util.DaoUtil;

@ExtendWith(MockitoExtension.class)
class ApiFootballStatsSnapshotServiceTest {

    @Mock
    private MatchService matchService;
    @Mock
    private EspnTeamSeasonStatsService espnTeamSeasonStatsService;

    @BeforeEach
    void teams() {
        for (int i = 1; i <= 20; i++) {
            DaoUtil.TEAMS.put(i, Team.builder().id(i).code("T" + i).name("Team " + i).build());
        }
    }

    @AfterEach
    void clear() {
        for (int i = 1; i <= 20; i++) {
            DaoUtil.TEAMS.remove(i);
        }
    }

    @Test
    void xgMetricsReadyThresholds() {
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(7, 7));
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(8, 12));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(8, 10));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(16, 20));
    }

    @Test
    void fetchSnapshotBuildsFromDbMatchesAndEspnXg() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(10).atTime(15, 0);
        List<Match> finished = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int home = i + 1;
            int away = (i + 1) % 20 + 1;
            finished.add(Match.builder()
                    .id(1000 + i)
                    .homeTeamId(home)
                    .awayTeamId(away)
                    .homeScore(1)
                    .awayScore(0)
                    .status("ft")
                    .startsAt(kickoff.plusDays(i))
                    .build());
        }
        when(matchService.findFinishedMatches()).thenReturn(finished);
        when(espnTeamSeasonStatsService.forInternalCode(anyString()))
                .thenReturn(new TeamSeasonExtras(null, null, null, null, null, null, 1.4, 1.1));

        ApiFootballStatsSnapshotService service =
                new ApiFootballStatsSnapshotService(matchService, espnTeamSeasonStatsService);
        LeagueStatsSnapshot snap = service.fetchSnapshot();
        assertEquals(20, snap.teams().size());
        assertNotNull(snap.league());
        assertEquals(1.4, snap.teams().getFirst().xgOverall());
        assertEquals(1.1, snap.teams().getFirst().xgaOverall());
    }
}
