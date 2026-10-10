package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.notification.GoalEventGifRenderer.Request;

class GoalSceneVideoRendererTest {

    @Test
    void configCarriesScoresMinuteAndDisallowedFlag() {
        Request req = new Request(0, 0, "MCI", "LIV", 1, 0, 2, 0, "Haaland  67'", false, "67'");
        var c = GoalSceneVideoRenderer.config(req);
        assertEquals("MCI", c.get("home"));
        assertEquals(List.of(1, 0), c.get("prev"));
        assertEquals(List.of(2, 0), c.get("next"));
        assertEquals("67'", c.get("minute"));
        assertEquals(false, c.get("disallowed"));
    }

    @Test
    void minuteIsOptionalForOldCallers() {
        Request req = new Request(1, 2, "A", "B", 2, 0, 1, 0, "VAR  ·  offside", true);
        assertNull(req.minute());
        assertEquals("", GoalSceneVideoRenderer.config(req).get("minute"));
    }

    @Test
    void disabledRendererReturnsNullSoCallerFallsBackToGif() {
        GoalSceneVideoRenderer r = new GoalSceneVideoRenderer(mock(HtmlImageRenderer.class), false);
        assertEquals(false, r.isAvailable());
        assertNull(r.render(new Request(1, 2, "A", "B", 0, 0, 1, 0, null, false)));
    }

    @Test
    void rendersGoalAndDisallowedMp4WhenChromeAndFfmpegExist() throws Exception {
        String chrome = findChrome();
        assumeTrue(chrome != null, "no Chromium; skipping");
        HtmlImageRenderer html = new HtmlImageRenderer(mock(PanicSender.class), null, null, null);
        ReflectionTestUtils.setField(html, "chromeExecutable", chrome);
        GoalSceneVideoRenderer renderer = new GoalSceneVideoRenderer(html, true);
        assumeTrue(renderer.isAvailable(), "no ffmpeg; skipping");

        Path out = Path.of("target/preview-cards");
        Files.createDirectories(out);
        for (Request req : List.of(
                new Request(0, 0, "MCI", "LIV", 1, 0, 2, 0, "Haaland  67'", false, "67'"),
                new Request(0, 0, "MCI", "LIV", 2, 0, 1, 0, "VAR  ·  offside", true, "71'"))) {
            String path = renderer.render(req);
            assertNotNull(path);
            Path dest = out.resolve(req.disallowed() ? "goal-video-disallowed.mp4" : "goal-video.mp4");
            Files.copy(Path.of(path), dest, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(Path.of(path));
            assertTrue(Files.size(dest) > 20_000);
        }
    }

    private static String findChrome() {
        for (String c : new String[]{
                System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH"), "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser", "/usr/bin/chromium", "/opt/pw-browsers/chromium"}) {
            if (c != null && Files.isExecutable(Path.of(c)) && !Files.isDirectory(Path.of(c))) {
                return c;
            }
        }
        return null;
    }
}
