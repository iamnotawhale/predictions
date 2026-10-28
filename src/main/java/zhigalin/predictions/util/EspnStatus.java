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

    private EspnStatus() {
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
