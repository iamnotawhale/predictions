package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.util.DaoUtil;

@ExtendWith(MockitoExtension.class)
class BettingRecommendationServiceSingleFlightTest {

    @Mock
    private FootyStatsScraperService scraperService;
    @Mock
    private SoccerStatsScraperService soccerStatsScraperService;
    @Mock
    private FootyStatsStatsDao statsDao;
    @Mock
    private MatchService matchService;
    @Mock
    private OddsService oddsService;
    @Mock
    private HeadToHeadService headToHeadService;

    @InjectMocks
    private BettingRecommendationService service;

    @Test
    void refreshForWeek_singleFlightSharesOneScrape() throws Exception {
        DaoUtil.TEAMS.clear();
        DaoUtil.TEAMS.put(1, Team.builder().publicId(1).code("ARS").name("Arsenal").build());
        DaoUtil.TEAMS.put(2, Team.builder().publicId(2).code("CHE").name("Chelsea").build());

        AtomicInteger scrapeCalls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        FootyStatsLeagueSnapshot league = new FootyStatsLeagueSnapshot(1, 1.4, 1.2, 1.2, 1.4, Instant.now());
        FootyStatsTeamSnapshot ars = emptyTeam("ARS");
        FootyStatsTeamSnapshot che = emptyTeam("CHE");
        FootyStatsScraperService.ParsedSnapshot snapshot =
                new FootyStatsScraperService.ParsedSnapshot(List.of(ars, che), league);

        when(scraperService.fetchSnapshot()).thenAnswer(inv -> {
            scrapeCalls.incrementAndGet();
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
            }
            return snapshot;
        });
        when(soccerStatsScraperService.enrich(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(statsDao.findLeagueSnapshot(1)).thenReturn(Optional.of(league));
        when(statsDao.findTeamStats(1)).thenReturn(List.of(ars, che));
        when(matchService.findAllByWeekId(1)).thenReturn(List.of(
                Match.builder().publicId(10).weekId(1).homeTeamId(1).awayTeamId(2).status("ns").build()
        ));
        when(oddsService.getOdd(anyInt())).thenReturn(null);
        when(headToHeadService.findForRecommender(anyInt(), anyInt())).thenReturn(List.of());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> service.refreshForWeek(1));
            Future<Integer> second = pool.submit(() -> {
                if (!started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start timeout");
                }
                return service.refreshForWeek(1);
            });
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("scrape did not start");
            }
            Thread.sleep(50);
            release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, scrapeCalls.get());
            verify(scraperService, times(1)).fetchSnapshot();
            verify(statsDao, atLeastOnce()).saveRecommendations(anyList());
        } finally {
            release.countDown();
            pool.shutdownNow();
            DaoUtil.TEAMS.clear();
        }
    }

    private static FootyStatsTeamSnapshot emptyTeam(String code) {
        return new FootyStatsTeamSnapshot(
                code, 1, 1, 1, 1, 1, 1,
                1.0, 1.0, 0.0, 1.0, 1.0,
                FootyStatsExtendedMetrics.empty(),
                Instant.now()
        );
    }
}
