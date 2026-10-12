package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.TipTrackRecord;

class TipTrackRecordTest {

    @Test
    void averagesEplPointsOfFrozenTips() {
        TipTrackRecord r = BettingRecommendationService.computeTrackRecord(List.of(
                new int[]{2, 1, 2, 1},
                new int[]{1, 0, 2, 1},
                new int[]{2, 1, 3, 0},
                new int[]{1, 1, 2, 0}
        ));
        assertEquals(4, r.matches());
        assertEquals(1, r.exact());
        assertEquals((4 + 2 + 1 - 1) / 4.0, r.avgPoints(), 1e-9);
    }

    @Test
    void emptySeasonHasNoMatches() {
        TipTrackRecord r = BettingRecommendationService.computeTrackRecord(List.of());
        assertEquals(0, r.matches());
        assertEquals(0.0, r.avgPoints(), 1e-9);
    }
}
