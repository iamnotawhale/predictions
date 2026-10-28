package zhigalin.predictions.util;

import java.util.Set;

import zhigalin.predictions.model.v2.Status;
import zhigalin.predictions.model.v2.Type;

/** ESPN status helpers shared by EPL and cup sync. */
public final class EspnStatus {

    /**
     * Fixtures that will be replayed later. ESPN reports them with {@code state=post}
     * and {@code completed=false}, so they must not be treated as full time.
     */
    private static final Set<String> NOT_PLAYED = Set.of(
            "STATUS_POSTPONED",
            "STATUS_CANCELED",
            "STATUS_CANCELLED",
            "STATUS_SUSPENDED",
            "STATUS_ABANDONED"
    );

    private static final int LAST_REGULATION_PERIOD = 2;

    private EspnStatus() {
    }

    /**
     * True once the match has gone past 90 minutes: extra time, shootout, or a final after either.
     * ESPN numbers periods 1–2 for regulation, 3–4 for extra time and 5 for the shootout.
     */
    public static boolean isBeyondRegulation(Status status) {
        if (status == null) {
            return false;
        }
        if (status.getPeriod() != null && status.getPeriod() > LAST_REGULATION_PERIOD) {
            return true;
        }
        Type type = status.getType();
        String name = type != null && type.getName() != null ? type.getName().toUpperCase() : "";
        return name.contains("EXTRA")
               || name.contains("OVERTIME")
               || name.contains("SHOOTOUT")
               || name.endsWith("_AET")
               || name.endsWith("_PEN");
    }

    public static boolean isPostponed(Status status) {
        return status != null && isPostponed(status.getType());
    }

    public static boolean isPostponed(Type type) {
        if (type == null) {
            return false;
        }
        if (type.getName() != null && NOT_PLAYED.contains(type.getName().toUpperCase())) {
            return true;
        }
        return "post".equalsIgnoreCase(type.getState()) && Boolean.FALSE.equals(type.getCompleted());
    }
}
