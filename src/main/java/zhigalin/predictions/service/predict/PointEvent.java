package zhigalin.predictions.service.predict;

import java.time.LocalDateTime;
import java.util.Comparator;

/** One finished (or live-provisional) point event in chronological order. */
public record PointEvent(
        String login,
        int userId,
        LocalDateTime sortTime,
        long sequence,
        int weekId,
        int rawPoints,
        boolean cup
) {
    public static final Comparator<PointEvent> BY_TIME = Comparator
            .comparing(PointEvent::sortTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(PointEvent::sequence);
}
