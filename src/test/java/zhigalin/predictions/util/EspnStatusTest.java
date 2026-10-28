package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.v2.Status;
import zhigalin.predictions.model.v2.Type;

class EspnStatusTest {

    private static Status status(String name, String state, Boolean completed) {
        Type type = new Type();
        type.setName(name);
        type.setState(state);
        type.setCompleted(completed);
        Status status = new Status();
        status.setType(type);
        return status;
    }

    @Test
    void postponedAndCanceledAreNotFullTime() {
        assertTrue(EspnStatus.isPostponed(status("STATUS_POSTPONED", "post", false)));
        assertTrue(EspnStatus.isPostponed(status("STATUS_CANCELED", "post", false)));
        assertTrue(EspnStatus.isPostponed(status("STATUS_ABANDONED", "post", false)));
    }

    @Test
    void unknownPostStateThatIsNotCompletedCountsAsPostponed() {
        assertTrue(EspnStatus.isPostponed(status(null, "post", false)));
    }

    @Test
    void playedMatchesAreNotPostponed() {
        assertFalse(EspnStatus.isPostponed(status("STATUS_FULL_TIME", "post", true)));
        assertFalse(EspnStatus.isPostponed(status("STATUS_FINAL_AET", "post", true)));
        assertFalse(EspnStatus.isPostponed(status("STATUS_FINAL_PEN", "post", true)));
        assertFalse(EspnStatus.isPostponed(status(null, "post", null)));
        assertFalse(EspnStatus.isPostponed(status("STATUS_SCHEDULED", "pre", false)));
        assertFalse(EspnStatus.isPostponed(status("STATUS_FIRST_HALF", "in", false)));
        assertFalse(EspnStatus.isPostponed((Status) null));
    }
}
