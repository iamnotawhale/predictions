package zhigalin.predictions.service.notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import zhigalin.predictions.util.EspnKitColors;
import zhigalin.predictions.util.ProcessLaunch;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoalSceneVideoRenderer {

    private static final Logger log = LoggerFactory.getLogger("server");
    static final int SIZE = 480;
    static final int FPS = 25;
    static final double DURATION = 9.0;
    static final List<String> LOGO_MOTIONS = List.of(
            "settle", "float", "breathe", "tilt", "pendulum", "shimmer", "drift-in-out");
    private static final String SCENE = "/notification-cards/goal-scene.html";

    private final HtmlImageRenderer html;
    private final boolean enabled;
    private final String ffmpegPath;

    public GoalSceneVideoRenderer(
            HtmlImageRenderer html,
            @Value("${notification.goal-gif.video-enabled:true}") boolean enabled
    ) {
        this.html = html;
        this.enabled = enabled;
        this.ffmpegPath = enabled ? findFfmpeg() : null;
        if (enabled && ffmpegPath == null) {
            log.info("ffmpeg not found - goal animation falls back to the Java GIF");
        }
    }

    public boolean isAvailable() {
        return enabled && ffmpegPath != null;
    }

    public String render(GoalEventGifRenderer.Request req) {
        if (!isAvailable() || req == null) {
            return null;
        }
        Path frames = null;
        try {
            frames = Files.createTempDirectory("goal-frames-");
            Path dir = frames;
            Path page = dir.resolve("scene.html");
            Files.writeString(page, loadScene(), StandardCharsets.UTF_8);
            Map<String, Object> config = config(req);
            Boolean ok = html.withPage(SIZE, SIZE, p -> captureFrames(p, page, config, dir));
            if (!Boolean.TRUE.equals(ok)) {
                log.warn("Goal video: Chromium frame capture failed");
                return null;
            }
            Path out = Files.createTempFile("goal-event-", ".mp4");
            if (!encode(dir, out)) {
                Files.deleteIfExists(out);
                return null;
            }
            log.info("Goal video written {} ({} KB, disallowed={})",
                    out.getFileName(), Files.size(out) / 1024, req.disallowed());
            return out.toAbsolutePath().toString();
        } catch (Exception e) {
            log.warn("Goal video render failed: {}", e.getMessage());
            return null;
        } finally {
            deleteQuietly(frames);
        }
    }

    static Map<String, Object> config(GoalEventGifRenderer.Request req) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("home", req.homeCode());
        c.put("away", req.awayCode());
        c.put("homeLogo", CardLogos.resolveLogoSrc(req.homeTeamId(), null));
        c.put("awayLogo", CardLogos.resolveLogoSrc(req.awayTeamId(), null));
        c.put("prev", List.of(req.prevHome(), req.prevAway()));
        c.put("next", List.of(req.nextHome(), req.nextAway()));
        c.put("subtitle", req.subtitle() == null ? "" : req.subtitle());
        c.put("minute", req.minute() == null ? "" : req.minute());
        c.put("disallowed", req.disallowed());
        c.put("logo", !req.disallowed());
        c.put("homeKit", kitMap(req.homeKit()));
        c.put("awayKit", kitMap(req.awayKit()));
        c.put("logoMotion", LOGO_MOTIONS.get(ThreadLocalRandom.current().nextInt(LOGO_MOTIONS.size())));
        return c;
    }

    private static Map<String, String> kitMap(EspnKitColors.Kit kit) {
        if (kit == null) {
            return null;
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("color", kit.color());
        m.put("alternateColor", kit.alternateColor());
        return m;
    }

    private static Boolean captureFrames(Page page, Path scene, Map<String, Object> config, Path dir) {
        page.navigate(scene.toUri().toString(), new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(15_000));
        // Web fonts come from Google Fonts; do not hang if the CDN is slow.
        page.evaluate("Promise.race([document.fonts.ready, new Promise(r => setTimeout(r, 3000))])");
        page.evaluate("c => setup(c)", config);
        int n = (int) Math.round(FPS * DURATION);
        for (int i = 0; i < n; i++) {
            page.evaluate("t => seek(t)", i / (double) FPS);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(String.format("%04d.jpg", i)))
                    .setType(ScreenshotType.JPEG)
                    .setQuality(92));
        }
        return Boolean.TRUE;
    }

    private boolean encode(Path dir, Path out) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                ffmpegPath, "-y", "-loglevel", "error",
                "-framerate", String.valueOf(FPS),
                "-i", dir.resolve("%04d.jpg").toAbsolutePath().toString(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
                out.toAbsolutePath().toString());
        ProcessLaunch.Result r = ProcessLaunch.run(cmd, 60);
        if (r.exitCode() != 0 || Files.size(out) == 0) {
            log.warn("ffmpeg failed ({}): {}", r.exitCode(), r.output().strip());
            return false;
        }
        return true;
    }

    private static String loadScene() throws IOException {
        try (InputStream in = GoalSceneVideoRenderer.class.getResourceAsStream(SCENE)) {
            if (in == null) {
                throw new IOException("missing " + SCENE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String findFfmpeg() {
        for (String candidate : new String[]{"/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg", "ffmpeg"}) {
            try {
                ProcessLaunch.runCapture(List.of(candidate, "-version"), 5);
                Path abs = Path.of(candidate);
                if (abs.isAbsolute() && Files.isExecutable(abs)) {
                    return abs.toString();
                }
                return candidate;
            } catch (Exception e) {
                if (ProcessLaunch.isSpawnHelperFailure(e)) {
                    log.warn("ffmpeg probe spawn helper failed for {}: {}", candidate, e.getMessage());
                }
            }
        }
        return null;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
        }
    }
}
