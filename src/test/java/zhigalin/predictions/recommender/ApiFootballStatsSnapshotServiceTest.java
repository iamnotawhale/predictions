package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.service.api.ApiFootballClient;

@ExtendWith(MockitoExtension.class)
class ApiFootballStatsSnapshotServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ApiFootballClient apiFootballClient;
    @Mock
    private ApiFootballFixtureXgDao fixtureXgDao;

    @Test
    void xgMetricsReadyRequiresCoverageAndMinimumSamples() {
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(7, 7));
        assertFalse(ApiFootballStatsSnapshotService.xgMetricsReady(8, 12));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(8, 10));
        assertTrue(ApiFootballStatsSnapshotService.xgMetricsReady(16, 20));
    }

    @Test
    void parseExpectedGoalsReadsStringValues() throws Exception {
        var root = MAPPER.readTree("""
                {
                  "response": [
                    {"team":{"id":1},"statistics":[{"type":"expected_goals","value":"1.45"}]},
                    {"team":{"id":2},"statistics":[{"type":"expected_goals","value":"0.90"}]}
                  ]
                }
                """);
        ApiFootballStatsSnapshotService.DoublePair xg =
                ApiFootballStatsSnapshotService.parseExpectedGoals(root, 1, 2);
        assertNotNull(xg);
        assertEquals(1.45, xg.home(), 1e-9);
        assertEquals(0.90, xg.away(), 1e-9);
    }

    @Test
    void parseExpectedGoalsReturnsNullWhenMetricMissing() throws Exception {
        var root = MAPPER.readTree("""
                {
                  "response": [
                    {"team":{"id":1},"statistics":[{"type":"shots","value":10}]},
                    {"team":{"id":2},"statistics":[{"type":"shots","value":8}]}
                  ]
                }
                """);
        assertNull(ApiFootballStatsSnapshotService.parseExpectedGoals(root, 1, 2));
    }

    @Test
    void ensureXgCacheTombsMissingMetricAndContinuesToLaterFixtures() throws Exception {
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of());
        when(apiFootballClient.fetchFixtureStatistics(1)).thenReturn(MAPPER.readTree("""
                {"response":[
                  {"team":{"id":10},"statistics":[{"type":"shots","value":3}]},
                  {"team":{"id":20},"statistics":[{"type":"shots","value":4}]}
                ]}
                """));
        when(apiFootballClient.fetchFixtureStatistics(2)).thenReturn(MAPPER.readTree("""
                {"response":[
                  {"team":{"id":10},"statistics":[{"type":"expected_goals","value":"1.2"}]},
                  {"team":{"id":30},"statistics":[{"type":"expected_goals","value":"0.8"}]}
                ]}
                """));

        ApiFootballStatsSnapshotService service =
                new ApiFootballStatsSnapshotService(apiFootballClient, fixtureXgDao);
        service.ensureXgCache(List.of(
                new ApiFootballStatsSnapshotService.FinishedMatch(1, 10, 20, 1, 0, 0, 0, 1L),
                new ApiFootballStatsSnapshotService.FinishedMatch(2, 10, 30, 2, 1, 1, 0, 2L)
        ));

        verify(fixtureXgDao).upsertUnavailable(eq(1), eq(10), eq(20), any(Instant.class));
        verify(fixtureXgDao).upsert(eq(2), eq(10), eq(30), eq(1.2), eq(0.8), any(Instant.class));
    }

    @Test
    void ensureXgCacheSkipsTransportFailureWithoutTombstone() {
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of());
        when(apiFootballClient.fetchFixtureStatistics(1)).thenReturn(null);
        when(apiFootballClient.fetchFixtureStatistics(2)).thenReturn(null);

        ApiFootballStatsSnapshotService service =
                new ApiFootballStatsSnapshotService(apiFootballClient, fixtureXgDao);
        service.ensureXgCache(List.of(
                new ApiFootballStatsSnapshotService.FinishedMatch(1, 10, 20, 1, 0, null, null, 1L),
                new ApiFootballStatsSnapshotService.FinishedMatch(2, 10, 30, 0, 0, null, null, 2L)
        ));

        verify(fixtureXgDao, never()).upsertUnavailable(anyInt(), anyInt(), anyInt(), any());
        verify(fixtureXgDao, never()).upsert(anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble(), any());
        ArgumentCaptor<Integer> fixtureCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(apiFootballClient, org.mockito.Mockito.times(2)).fetchFixtureStatistics(fixtureCaptor.capture());
        assertEquals(List.of(1, 2), fixtureCaptor.getAllValues());
    }

    @Test
    void ensureXgCacheSkipsApiErrorEnvelopeWithoutTombstone() throws Exception {
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of());
        when(apiFootballClient.fetchFixtureStatistics(1)).thenReturn(MAPPER.readTree("""
                {"errors":{"rateLimit":"Too many requests"},"response":[]}
                """));
        when(apiFootballClient.fetchFixtureStatistics(2)).thenReturn(MAPPER.readTree("""
                {"response":[
                  {"team":{"id":10},"statistics":[{"type":"expected_goals","value":1.1}]},
                  {"team":{"id":30},"statistics":[{"type":"expected_goals","value":0.7}]}
                ]}
                """));

        ApiFootballStatsSnapshotService service =
                new ApiFootballStatsSnapshotService(apiFootballClient, fixtureXgDao);
        service.ensureXgCache(List.of(
                new ApiFootballStatsSnapshotService.FinishedMatch(1, 10, 20, 1, 0, null, null, 1L),
                new ApiFootballStatsSnapshotService.FinishedMatch(2, 10, 30, 2, 1, null, null, 2L)
        ));

        verify(fixtureXgDao, never()).upsertUnavailable(anyInt(), anyInt(), anyInt(), any());
        verify(fixtureXgDao).upsert(eq(2), eq(10), eq(30), eq(1.1), eq(0.7), any(Instant.class));
    }

    @Test
    void isTransportLikeStatsDetectsErrorEnvelope() throws Exception {
        assertTrue(ApiFootballStatsSnapshotService.isTransportLikeStats(
                MAPPER.readTree("{\"errors\":{\"rateLimit\":\"x\"},\"response\":[]}")));
        assertFalse(ApiFootballStatsSnapshotService.isTransportLikeStats(MAPPER.readTree("""
                {"response":[
                  {"team":{"id":1},"statistics":[]},
                  {"team":{"id":2},"statistics":[]}
                ]}
                """)));
    }

    @Test
    void ensureXgCacheCapsStatisticsCallsIncludingTransportSkips() {
        when(fixtureXgDao.findCachedFixtureIds(any())).thenReturn(Set.of());
        when(apiFootballClient.fetchFixtureStatistics(anyInt())).thenReturn(null);

        List<ApiFootballStatsSnapshotService.FinishedMatch> finished = new java.util.ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            finished.add(new ApiFootballStatsSnapshotService.FinishedMatch(
                    i, 10, 20 + i, 1, 0, null, null, i));
        }

        ApiFootballStatsSnapshotService service =
                new ApiFootballStatsSnapshotService(apiFootballClient, fixtureXgDao);
        service.ensureXgCache(finished);

        verify(apiFootballClient, org.mockito.Mockito.times(25)).fetchFixtureStatistics(anyInt());
        verify(apiFootballClient, never()).fetchFixtureStatistics(26);
        verify(fixtureXgDao, never()).upsertUnavailable(anyInt(), anyInt(), anyInt(), any());
    }
}
