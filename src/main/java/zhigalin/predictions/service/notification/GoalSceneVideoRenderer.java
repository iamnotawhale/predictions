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
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Goal / cancelled-goal animation: the broadcast-style HTML scene ({@code goal-scene.html}) is played
 * frame by frame in headless Chromium (animations are seeked, so timing is exact) and encoded to H.264
 * with ffmpeg. Telegram plays the mp4 as an animation, smoother and ~5x lighter than a GIF.
 * Returns null whenever Chromium or ffmpeg is unavailable so the caller can fall back to the Java GIF.
 */
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

    /** Renders the scene to a temp .mp4; the caller deletes the file. Null on any failure. */
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
        Process p = new ProcessBuilder(
                ffmpegPath, "-y", "-loglevel", "error",
                "-framerate", String.valueOf(FPS),
                "-i", dir.resolve("%04d.jpg").toString(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
                out.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            log.warn("ffmpeg timed out");
            return false;
        }
        if (p.exitValue() != 0 || Files.size(out) == 0) {
            log.warn("ffmpeg failed ({}): {}", p.exitValue(), output.strip());
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
        for (String candidate : new String[]{"ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"}) {
            try {
                Process p = new ProcessBuilder(candidate, "-version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
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
