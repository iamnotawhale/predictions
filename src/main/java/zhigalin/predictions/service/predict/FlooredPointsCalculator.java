package zhigalin.predictions.service.predict;

import java.util.List;

/**
 * Running season/week totals that never go below zero after each finished fixture.
 */
public final class FlooredPointsCalculator {

    private FlooredPointsCalculator() {
    }

    public static int applyFloor(List<Integer> orderedRawPoints) {
        int running = 0;
        if (orderedRawPoints == null) {
            return 0;
        }
        for (Integer raw : orderedRawPoints) {
            if (raw == null) {
                continue;
            }
            running = Math.max(0, running + raw);
        }
        return running;
    }

}
