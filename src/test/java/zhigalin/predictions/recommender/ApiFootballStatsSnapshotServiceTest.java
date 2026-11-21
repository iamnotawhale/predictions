package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.recommender.FotMobClient.FotMobFixture;
import zhigalin.predictions.recommender.FotMobClient.XgPair;
import zhigalin.predictions.recommender.UnderstatClient.UnderstatMatch;
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
    private FixtureXgDao fixtureXgDao;
    @Mock
    private UnderstatClient understatClient;
    @Mock
    private FotMobClient fotMobClient;
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

    private ApiFootballStatsSnapshotService service() {
        return new ApiFootballStatsSnapshotService(
                matchService, fixtureXgDao, understatClient, fotMobClient, espnTeamSeasonStatsService
        );
    }

    @Test
    void xgMetricsReadyThresholds() {
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(7, 7));
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(8, 12));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(8, 10));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(16, 20));
    }

    @Test
    void fetchSnapshotUsesFixtureXgAndSkipsEspnWhenCoverageOk() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(10).atTime(15, 0);
        List<Match> finished = new ArrayList<>();
        List<FixtureXgDao.XgRow> xgRows = new ArrayList<>();
        int matchId = 1000;
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 20; i++) {
                int home = i + 1;
                int away = ((i + 1 + round) % 20) + 1;
                if (home == away) {
                    away = (away % 20) + 1;
                }
                finished.add(Match.builder()
                        .id(matchId)
                        .homeTeamId(home)
                        .awayTeamId(away)
                        .homeScore(1)
                        .awayScore(0)
                        .htHome(1)
                        .htAway(0)
                        .status("ft")
                        .startsAt(kickoff.plusDays(matchId - 1000))
                        .build());
                xgRows.add(new FixtureXgDao.XgRow(matchId, home, away, 1.5, 0.8));
                matchId++;
            }
        }
        when(matchService.findFinishedMatches()).thenReturn(finished);
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.copyOf(
                finished.stream().map(Match::getId).toList()
        ));
        when(fixtureXgDao.findAllWithXg()).thenReturn(xgRows);
        when(understatClient.fetchFinishedWithXg(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());
        when(fotMobClient.fetchFinishedFixtures(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());

        LeagueStatsSnapshot snap = service().fetchSnapshot();
        assertEquals(20, snap.teams().size());
        assertNotNull(snap.league());
        assertEquals(1.15, snap.teams().getFirst().xgOverall(), 1e-9);
        assertEquals(1.15, snap.teams().getFirst().xgaOverall(), 1e-9);
        assertNotNull(snap.teams().getFirst().extendedOrEmpty().htPpg());
        verify(espnTeamSeasonStatsService, never()).forInternalCode(anyString());
    }

    @Test
    void fetchSnapshotFallsBackToEspnWhenMatchXgCoverageLow() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(10).atTime(15, 0);
        List<Match> finished = new ArrayList<>();
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
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of());
        when(fixtureXgDao.findAllWithXg()).thenReturn(List.of());
        when(understatClient.fetchFinishedWithXg(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());
        when(fotMobClient.fetchFinishedFixtures(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());
        when(espnTeamSeasonStatsService.forInternalCode(anyString()))
                .thenReturn(new TeamSeasonExtras(null, null, null, null, null, null, 1.4, 1.1));

        LeagueStatsSnapshot snap = service().fetchSnapshot();
        assertEquals(1.4, snap.teams().getFirst().xgOverall());
        assertEquals(1.1, snap.teams().getFirst().xgaOverall());
        assertNull(snap.teams().getFirst().extendedOrEmpty().recentXgPerMatch());
    }

    @Test
    void syncUnderstatPreferredBeforeFotMob() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(3).atTime(15, 0);
        for (int i = 1; i <= 20; i++) {
            DaoUtil.TEAMS.put(i, Team.builder().id(i).code("T" + i).name("Team " + i).build());
        }
        DaoUtil.TEAMS.put(1, Team.builder().id(1).code("ARS").name("Arsenal").build());
        DaoUtil.TEAMS.put(2, Team.builder().id(2).code("CHE").name("Chelsea").build());

        List<Match> finished = new ArrayList<>();
        finished.add(Match.builder()
                .id(42)
                .homeTeamId(1)
                .awayTeamId(2)
                .homeScore(2)
                .awayScore(1)
                .status("ft")
                .startsAt(kickoff)
                .build());
        for (int i = 3; i <= 20; i++) {
            int away = i == 20 ? 3 : i + 1;
            finished.add(Match.builder()
                    .id(100 + i)
                    .homeTeamId(i)
                    .awayTeamId(away)
                    .homeScore(1)
                    .awayScore(0)
                    .status("ft")
                    .startsAt(kickoff.plusDays(i))
                    .build());
        }

        Set<Integer> liveCache = new HashSet<>();
        when(matchService.findFinishedMatches()).thenReturn(finished);
        when(fixtureXgDao.findCachedFixtureIds(any())).thenAnswer(inv -> Set.copyOf(liveCache));
        doAnswer(inv -> {
            liveCache.add(inv.getArgument(0));
            return null;
        }).when(fixtureXgDao).upsert(anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble(), any(Instant.class));
        when(fixtureXgDao.findAllWithXg()).thenReturn(List.of(new FixtureXgDao.XgRow(42, 1, 2, 1.2, 0.9)));
        when(understatClient.fetchFinishedWithXg(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of(new UnderstatMatch("ARS", "CHE", 1.2, 0.9, kickoff.plusHours(1))));
        when(fotMobClient.fetchFinishedFixtures(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of(new FotMobFixture("fm-1", "ARS", "CHE", kickoff.plusHours(1))));
        when(espnTeamSeasonStatsService.forInternalCode(anyString()))
                .thenReturn(new TeamSeasonExtras(null, null, null, null, null, null, 1.0, 1.0));

        service().fetchSnapshot();
        verify(fixtureXgDao).upsert(eq(42), eq(1), eq(2), eq(1.2), eq(0.9), any(Instant.class));
        verify(fotMobClient, never()).fetchMatchXg(anyString());
    }

    @Test
    void syncUnderstatOverwritesExistingCache() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(3).atTime(15, 0);
        for (int i = 1; i <= 20; i++) {
            DaoUtil.TEAMS.put(i, Team.builder().id(i).code("T" + i).name("Team " + i).build());
        }
        DaoUtil.TEAMS.put(1, Team.builder().id(1).code("ARS").name("Arsenal").build());
        DaoUtil.TEAMS.put(2, Team.builder().id(2).code("CHE").name("Chelsea").build());

        List<Match> finished = new ArrayList<>();
        finished.add(Match.builder()
                .id(42)
                .homeTeamId(1)
                .awayTeamId(2)
                .homeScore(2)
                .awayScore(1)
                .status("ft")
                .startsAt(kickoff)
                .build());
        for (int i = 3; i <= 20; i++) {
            int away = i == 20 ? 3 : i + 1;
            finished.add(Match.builder()
                    .id(100 + i)
                    .homeTeamId(i)
                    .awayTeamId(away)
                    .homeScore(1)
                    .awayScore(0)
                    .status("ft")
                    .startsAt(kickoff.plusDays(i))
                    .build());
        }

        when(matchService.findFinishedMatches()).thenReturn(finished);
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of(42));
        when(fixtureXgDao.findAllWithXg()).thenReturn(List.of(new FixtureXgDao.XgRow(42, 1, 2, 9.9, 9.9)));
        when(understatClient.fetchFinishedWithXg(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of(new UnderstatMatch("ARS", "CHE", 1.2, 0.9, kickoff.plusHours(1))));
        when(fotMobClient.fetchFinishedFixtures(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());
        when(espnTeamSeasonStatsService.forInternalCode(anyString()))
                .thenReturn(new TeamSeasonExtras(null, null, null, null, null, null, 1.0, 1.0));

        service().fetchSnapshot();
        verify(fixtureXgDao).upsert(eq(42), eq(1), eq(2), eq(1.2), eq(0.9), any(Instant.class));
    }

    @Test
    void syncFotMobFillsWhenUnderstatEmpty() {
        LocalDateTime kickoff = DataInitService.seasonStartDate().plusDays(3).atTime(15, 0);
        for (int i = 1; i <= 20; i++) {
            DaoUtil.TEAMS.put(i, Team.builder().id(i).code("T" + i).name("Team " + i).build());
        }
        DaoUtil.TEAMS.put(1, Team.builder().id(1).code("ARS").name("Arsenal").build());
        DaoUtil.TEAMS.put(2, Team.builder().id(2).code("CHE").name("Chelsea").build());

        List<Match> finished = new ArrayList<>();
        finished.add(Match.builder()
                .id(42)
                .homeTeamId(1)
                .awayTeamId(2)
                .homeScore(2)
                .awayScore(1)
                .status("ft")
                .startsAt(kickoff)
                .build());
        for (int i = 3; i <= 20; i++) {
            int away = i == 20 ? 3 : i + 1;
            finished.add(Match.builder()
                    .id(100 + i)
                    .homeTeamId(i)
                    .awayTeamId(away)
                    .homeScore(1)
                    .awayScore(0)
                    .status("ft")
                    .startsAt(kickoff.plusDays(i))
                    .build());
        }

        when(matchService.findFinishedMatches()).thenReturn(finished);
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(new HashSet<>());
        when(fixtureXgDao.findAllWithXg()).thenReturn(List.of(new FixtureXgDao.XgRow(42, 1, 2, 1.2, 0.9)));
        when(understatClient.fetchFinishedWithXg(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of());
        when(fotMobClient.fetchFinishedFixtures(eq(DataInitService.SEASON), anyBoolean()))
                .thenReturn(List.of(new FotMobFixture("fm-1", "ARS", "CHE", kickoff.plusHours(1))));
        when(fotMobClient.fetchMatchXg("fm-1")).thenReturn(Optional.of(new XgPair(1.2, 0.9)));
        when(espnTeamSeasonStatsService.forInternalCode(anyString()))
                .thenReturn(new TeamSeasonExtras(null, null, null, null, null, null, 1.0, 1.0));

        service().fetchSnapshot();
        verify(fotMobClient).fetchMatchXg("fm-1");
        verify(fixtureXgDao).upsert(eq(42), eq(1), eq(2), eq(1.2), eq(0.9), any(Instant.class));
    }
}
