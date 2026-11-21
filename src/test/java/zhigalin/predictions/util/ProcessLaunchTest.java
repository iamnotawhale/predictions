package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessLaunchTest {

    @Test
    void spawnHelperFailureIsDetected() {
        assertTrue(ProcessLaunch.isSpawnHelperFailure(
                new IOException("Cannot run program \"ffmpeg\": Failed to exec spawn helper")));
        assertTrue(ProcessLaunch.isSpawnHelperFailure(
                new IOException("wrap", new IOException("Failed to exec spawn helper: exit code: 1"))));
        assertFalse(ProcessLaunch.isSpawnHelperFailure(new IOException("No such file")));
    }

    @Test
    void canRunFfmpegVersionWhenPresent() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")), "no /usr/bin/ffmpeg");
        String out = ProcessLaunch.runCapture(List.of("/usr/bin/ffmpeg", "-version"), 5);
        assertTrue(out.toLowerCase().contains("ffmpeg"));
    }
}
