package zhigalin.predictions.service.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.service.predict.UserPredictionStatsService.Bucket;

class UserPredictionStatsServiceTest {

    @Test
    void classifyBuckets() {
        assertEquals(Bucket.EXACT, UserPredictionStatsService.classify(2, 1, 2, 1));
        assertEquals(Bucket.GOAL_DIFF, UserPredictionStatsService.classify(3, 1, 2, 0));
        assertEquals(Bucket.OUTCOME, UserPredictionStatsService.classify(2, 1, 3, 0));
        assertEquals(Bucket.MISS, UserPredictionStatsService.classify(2, 1, 0, 1));
        assertEquals(Bucket.NO_BET, UserPredictionStatsService.classify(2, 1, null, null));
        // draws share GD=0 → sameDiff / GOAL_DIFF, not plain OUTCOME
        assertEquals(Bucket.GOAL_DIFF, UserPredictionStatsService.classify(1, 1, 0, 0));
    }
}
