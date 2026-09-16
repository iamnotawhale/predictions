package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JerseyThumbUrlTest {

    @Test
    void appendsWidthHeightToStitcherUrl() {
        String src = "https://stitcher.espn.com/sports/soccer/leagues/eng.1/events/1/athletes/2/jersey.png?darkMode=true";
        assertEquals(
                src + "&width=128&height=128",
                MiniAppService.withJerseyThumbSize(src)
        );
    }

    @Test
    void leavesSizedUrlUntouched() {
        String src = "https://example.com/j.png?width=64&height=64";
        assertEquals(src, MiniAppService.withJerseyThumbSize(src));
    }

    @Test
    void nullSafe() {
        assertNull(MiniAppService.withJerseyThumbSize(null));
        assertNull(MiniAppService.withJerseyThumbSize("  "));
    }
}
