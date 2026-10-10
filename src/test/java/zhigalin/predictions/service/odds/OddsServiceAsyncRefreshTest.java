package zhigalin.predictions.service.odds;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.service.api.EspnScoreboardClient;
import zhigalin.predictions.service.api.EspnTeamTotalsClient;

class OddsServiceAsyncRefreshTest {

    @Test
    void asyncRefreshReturnsImmediatelyEvenWhenEspnIsSlow() throws Exception {
        EspnScoreboardClient espn = mock(EspnScoreboardClient.class);
        CountDownLatch release = new CountDownLatch(1);
        when(espn.fetchScoreboard(any(LocalDate.class), any(LocalDate.class))).thenAnswer(inv -> {
            release.await(10, TimeUnit.SECONDS);
            return new Scoreboard();
        });
        OddsService service = new OddsService(
                mock(PanicSender.class), mock(MatchDao.class), espn, mock(EspnTeamTotalsClient.class));
        Match match = new Match();
        match.setLocalDateTime(LocalDateTime.now().plusDays(1));

        long started = System.nanoTime();
        service.refreshAsyncIfStale(List.of(match));
        service.refreshAsyncIfStale(List.of(match)); // second call within the interval: no-op
        long tookMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(tookMs < 500, "caller must not wait for ESPN, took " + tookMs + " ms");
        verify(espn, timeout(2000)).fetchScoreboard(any(LocalDate.class), any(LocalDate.class));
        release.countDown();
    }
}
