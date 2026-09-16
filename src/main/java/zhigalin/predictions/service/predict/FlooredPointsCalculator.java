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

    /** Snapshot after each event: same length as input. */
    public static int[] runningSeries(List<Integer> orderedRawPoints) {
        if (orderedRawPoints == null || orderedRawPoints.isEmpty()) {
            return new int[0];
        }
        int[] series = new int[orderedRawPoints.size()];
        int running = 0;
        for (int i = 0; i < orderedRawPoints.size(); i++) {
            Integer raw = orderedRawPoints.get(i);
            if (raw != null) {
                running = Math.max(0, running + raw);
            }
            series[i] = running;
        }
        return series;
    }
}
