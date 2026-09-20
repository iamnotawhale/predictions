package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.service.api.TeamLogoCacheService;

/**
 * Writes goal / no-goal GIF previews to target/preview-cards/.
 * Logos load via ESPN fallback when cache returns null.
 */
class GoalEventGifPreviewTest {

    @Test
    void writesGoalAndDisallowedGifs() throws Exception {
        TeamLogoCacheService logos = mock(TeamLogoCacheService.class);
        when(logos.bytesForTeam(anyInt())).thenReturn(null);
        GoalEventGifRenderer renderer = new GoalEventGifRenderer(logos, true);

        Path outDir = Path.of("target/preview-cards");
        Files.createDirectories(outDir);

        String goalPath = renderer.renderToTempFile(new GoalEventGifRenderer.Request(
                50, 40, "MCI", "LIV", 1, 0, 2, 0, "Haaland  67'", false
        ));
        assertNotNull(goalPath);
        Path goalDest = outDir.resolve("goal-animation-test.gif");
        Files.copy(Path.of(goalPath), goalDest, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(Path.of(goalPath));
        assertTrue(Files.size(goalDest) > 10_000);

        String noPath = renderer.renderToTempFile(new GoalEventGifRenderer.Request(
                50, 40, "MCI", "LIV", 2, 0, 1, 0, "VAR  ·  offside", true
        ));
        assertNotNull(noPath);
        Path noDest = outDir.resolve("goal-disallowed-animation-test.gif");
        Files.copy(Path.of(noPath), noDest, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(Path.of(noPath));
        assertTrue(Files.size(noDest) > 10_000);

        System.out.println("goal gif KB=" + Files.size(goalDest) / 1024
                + " disallowed KB=" + Files.size(noDest) / 1024);
    }
}
