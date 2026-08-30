package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FootyStatsScraperServiceFetchTest {

    @Test
    void shouldRetryKnownBlockedStatuses() {
        assertTrue(FootyStatsScraperService.shouldRetryHttpStatus(403));
        assertTrue(FootyStatsScraperService.shouldRetryHttpStatus(429));
        assertFalse(FootyStatsScraperService.shouldRetryHttpStatus(404));
        assertFalse(FootyStatsScraperService.shouldRetryHttpStatus(200));
    }

    @Test
    void parsesHttpStatusFromErrorMessage() {
        assertEquals(
                403,
                FootyStatsScraperService.httpStatusFromMessage(
                        "FootyStats request failed for https://footystats.org/england/premier-league/form-table: 403"
                )
        );
    }
}
