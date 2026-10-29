package zhigalin.predictions.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.TipTrackRecord;

class CupMatchTipServiceTest {

    @Test
    void emptyRecordWhenNoFinishedTips() {
        assertEquals(new TipTrackRecord(0, 0.0, 0), CupMatchTipService.computeTrackRecord(List.of()));
    }

    @Test
    void scoresTipsByCupRulesOnNinetyMinuteResult() {
        List<int[]> rows = List.of(
                new int[]{2, 1, 2, 1},
                new int[]{1, 0, 2, 1},
                new int[]{0, 0, 2, 1}
        );
        assertEquals(new TipTrackRecord(3, 1.0, 1), CupMatchTipService.computeTrackRecord(rows));
    }
}
