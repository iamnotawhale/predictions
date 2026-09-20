package zhigalin.predictions.service.notification;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.service.api.TeamLogoCacheService;

@Service
public class GoalEventGifRenderer {

    private static final Logger log = LoggerFactory.getLogger("server");

    private static final int SIZE = 400;
    private static final int FPS = 10;
    private static final double DURATION = 3.5;
    private static final Color ACCENT = new Color(176, 140, 255);
    private static final Color ACCENT_LIFT = new Color(201, 180, 255);
    private static final Color HINT = new Color(154, 143, 176);
    private static final Color WHITE = Color.WHITE;
    private static final Color GOAL_FLASH = new Color(255, 230, 120);
    private static final Color GOLD = new Color(255, 210, 110);
    private static final Color BG2 = new Color(20, 17, 26);
    private static final Color CANCEL = new Color(232, 96, 110);
    private static final Color CANCEL_LIFT = new Color(255, 170, 180);
    private static final Color TEAM_HOME = new Color(108, 186, 232);
    private static final Color TEAM_AWAY = new Color(220, 72, 92);

    private final TeamLogoCacheService logoCache;
    private final boolean enabled;
    private final BufferedImage staticBg;
    private final Font fontBadge;
    private final Font fontCode;
    private final Font fontSub;
    private final Font fontTitle;
    private final Font fontScore;

    public GoalEventGifRenderer(
            TeamLogoCacheService logoCache,
            @Value("${notification.goal-gif.enabled:true}") boolean enabled
    ) {
        this.logoCache = logoCache;
        this.enabled = enabled;
        this.staticBg = loadBackground();
        this.fontBadge = loadFont(15f, true);
        this.fontCode = loadFont(22f, true);
        this.fontSub = loadFont(15f, false);
        this.fontTitle = loadFont(48f, true);
        this.fontScore = loadFont(40f, true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record Request(
            int homeTeamId,
            int awayTeamId,
            String homeCode,
            String awayCode,
            int prevHome,
            int prevAway,
            int nextHome,
            int nextAway,
            String subtitle,
            boolean disallowed
    ) {
    }

    public String renderToTempFile(Request req) {
        if (!enabled || req == null) {
            return null;
        }
        try {
            BufferedImage homeLogo = loadLogo(req.homeTeamId(), req.homeCode());
            BufferedImage awayLogo = loadLogo(req.awayTeamId(), req.awayCode());
            int n = Math.max(2, (int) Math.round(DURATION * FPS));
            List<BufferedImage> frames = new ArrayList<>(n + 4);
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                frames.add(req.disallowed()
                        ? frameDisallowed(t, req, homeLogo, awayLogo)
                        : frameGoal(t, req, homeLogo, awayLogo));
            }
            int hold = Math.max(1, (int) Math.round(0.35 * FPS));
            BufferedImage last = frames.get(frames.size() - 1);
            for (int i = 0; i < hold; i++) {
                frames.add(copy(last));
            }

            Path out = Files.createTempFile("goal-event-", ".gif");
            writeGif(frames, out);
            out = maybeOptimizeWithGifsicle(out);
            long kb = Files.size(out) / 1024;
            log.info("Goal GIF written {} ({} KB, frames={}, disallowed={})",
                    out.getFileName(), kb, frames.size(), req.disallowed());
            return out.toAbsolutePath().toString();
        } catch (Exception e) {
            log.warn("Goal GIF render failed: {}", e.getMessage());
            return null;
        }
    }

    private void writeGif(List<BufferedImage> frames, Path out) throws IOException {
        int delayCs = Math.max(2, (int) Math.round(100.0 / FPS));
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(out));
             GifSequenceWriter writer = new GifSequenceWriter(ios, BufferedImage.TYPE_INT_RGB, delayCs, true)) {
            for (BufferedImage frame : frames) {
                writer.writeToSequence(frame);
            }
        }
    }

    private static Path maybeOptimizeWithGifsicle(Path raw) {
        String gifsicle = resolveGifsicleBinary();
        if (gifsicle == null) {
            return raw;
        }
        try {
            Path optimized = Files.createTempFile("goal-event-opt-", ".gif");
            // -O0: keep full frames. -O2/-O3 crop+transparency → stacking ghosts in Telegram.
            ProcessBuilder pb = new ProcessBuilder(
                    gifsicle,
                    "-O0",
                    "--disposal=background",
                    "--lossy=45",
                    "--colors", "128",
                    "-o", optimized.toAbsolutePath().toString(),
                    raw.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code == 0 && Files.size(optimized) > 0) {
                Files.deleteIfExists(raw);
                return optimized;
            }
            Files.deleteIfExists(optimized);
        } catch (Exception e) {
            log.debug("gifsicle optimize skipped: {}", e.getMessage());
        }
        return raw;
    }

    private static String resolveGifsicleBinary() {
        for (String candidate : new String[]{
                "gifsicle",
                "/usr/bin/gifsicle",
                "/usr/local/bin/gifsicle",
                System.getProperty("user.home") + "/bin/gifsicle"
        }) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private BufferedImage frameGoal(double t, Request req, BufferedImage homeLogo, BufferedImage awayLogo) {
        int scoreHome = req.prevHome();
        int scoreAway = req.prevAway();
        double scoreScale = 1.0;
        double scoreGlow = 0.0;
        double scoreAccent = 0.0;
        double sheetLift = 0.0;
        double flash = 0.0;

        TitleEnv te = titleEnvelope(t);
        flash = te.flash;

        if (t < 0.40) {
            sheetLift = t < 0.10 ? 0 : smoothstep((Math.min(t, 0.30) - 0.10) / 0.20) * 4;
            if (t >= 0.30) {
                sheetLift = 4;
            }
        } else if (t < 0.58) {
            double u = (t - 0.40) / 0.18;
            sheetLift = 4 * (1 - u * 0.25);
            if (u < 0.12) {
                scoreHome = req.prevHome();
                scoreScale = lerp(1.0, 0.84, u / 0.12);
                scoreGlow = (u / 0.12) * 0.35;
            } else {
                scoreHome = req.nextHome();
                scoreAway = req.nextAway();
                double v = (u - 0.12) / 0.88;
                if (v < 0.4) {
                    double w = easeOut(v / 0.4);
                    scoreScale = lerp(0.78, 1.22, w);
                    scoreGlow = lerp(0.4, 0.95, w);
                    scoreAccent = 1.0;
                    flash = Math.max(flash, 0.3 * (1 - w * 0.4));
                } else {
                    double w = easeInOut((v - 0.4) / 0.6);
                    scoreScale = lerp(1.22, 1.0, w);
                    scoreGlow = lerp(0.95, 0.35, w);
                    scoreAccent = lerp(1.0, 0.25, w);
                }
            }
        } else if (t < 0.78) {
            double u = smoothstep((t - 0.58) / 0.20);
            scoreHome = req.nextHome();
            scoreAway = req.nextAway();
            sheetLift = 2 * (1 - u);
            scoreGlow = lerp(0.35, 0.08, u);
            scoreAccent = lerp(0.25, 0.0, u);
        } else {
            scoreHome = req.nextHome();
            scoreAway = req.nextAway();
            scoreGlow = 0.05;
        }

        return paintFrame(
                req.homeCode(), req.awayCode(), homeLogo, awayLogo,
                scoreHome, scoreAway, scoreScale, scoreGlow, scoreAccent,
                sheetLift, te, req.subtitle(), false, 0,
                true, GOLD, GOAL_FLASH,
                mix(GOLD, WHITE, 0.25), mix(GOLD, ACCENT, 0.45), mix(BG2, mix(ACCENT, GOLD, 0.35), 0.38)
        );
    }

    private BufferedImage frameDisallowed(double t, Request req, BufferedImage homeLogo, BufferedImage awayLogo) {
        int scoreHome = req.prevHome();
        int scoreAway = req.prevAway();
        double scoreScale = 1.0;
        double scoreGlow = 0.0;
        double scoreAccent = 0.0;
        double sheetLift = 0.0;
        double flash = 0.0;
        boolean struck = false;
        double strikeT = 0.0;

        TitleEnv te = titleEnvelope(t);
        flash = te.flash;

        if (t < 0.40) {
            sheetLift = t < 0.10 ? 0 : smoothstep((Math.min(t, 0.30) - 0.10) / 0.20) * 4;
            if (t >= 0.30) {
                sheetLift = 4;
            }
        } else if (t < 0.58) {
            double u = (t - 0.40) / 0.18;
            sheetLift = 4 * (1 - u * 0.25);
            struck = true;
            strikeT = easeOut(Math.min(1.0, u * 1.25));
            scoreScale = lerp(1.0, 0.88, easeOut(u));
            scoreGlow = 0.3 * u;
            scoreAccent = 0.7 * u;
            flash = Math.max(flash, 0.12 * Math.sin(u * Math.PI));
        } else if (t < 0.78) {
            double u = (t - 0.58) / 0.20;
            sheetLift = 2 * (1 - smoothstep(u));
            if (u < 0.22) {
                struck = true;
                strikeT = 1.0;
                scoreScale = lerp(0.88, 0.72, u / 0.22);
                scoreGlow = 0.35;
                scoreAccent = 0.8;
            } else {
                scoreHome = req.nextHome();
                scoreAway = req.nextAway();
                double v = (u - 0.22) / 0.78;
                if (v < 0.4) {
                    double w = easeOut(v / 0.4);
                    scoreScale = lerp(0.72, 1.18, w);
                    scoreGlow = lerp(0.35, 0.9, w);
                    scoreAccent = 1.0;
                    flash = Math.max(flash, 0.25 * (1 - w * 0.4));
                } else {
                    double w = easeInOut((v - 0.4) / 0.6);
                    scoreScale = lerp(1.18, 1.0, w);
                    scoreGlow = lerp(0.9, 0.28, w);
                    scoreAccent = lerp(1.0, 0.22, w);
                }
                if (u > 0.55) {
                    double w = smoothstep((u - 0.55) / 0.45);
                    scoreGlow = lerp(scoreGlow, 0.08, w);
                    scoreAccent = lerp(scoreAccent, 0.0, w);
                }
            }
        } else {
            scoreHome = req.nextHome();
            scoreAway = req.nextAway();
            scoreGlow = 0.05;
        }

        return paintFrame(
                req.homeCode(), req.awayCode(), homeLogo, awayLogo,
                scoreHome, scoreAway, scoreScale, scoreGlow, scoreAccent,
                sheetLift, te, req.subtitle(), struck, strikeT,
                false, CANCEL_LIFT, mix(CANCEL, new Color(255, 90, 120), 0.35),
                mix(WHITE, CANCEL_LIFT, 0.15), mix(CANCEL, CANCEL_LIFT, 0.5), mix(BG2, CANCEL, 0.42)
        );
    }

    private BufferedImage paintFrame(
            String homeCode, String awayCode,
            BufferedImage homeLogo, BufferedImage awayLogo,
            int scoreHome, int scoreAway,
            double scoreScale, double scoreGlow, double scoreAccent,
            double sheetLift, TitleEnv te, String subtitle,
            boolean struck, double strikeT,
            boolean goalMode,
            Color titleA, Color titleB,
            Color badgeFill, Color badgeOutline, Color badgeBg
    ) {
        BufferedImage img = copy(staticBg);
        Graphics2D g = img.createGraphics();
        enableQuality(g);

        int logo = 44;
        int padX = 18;
        int padY = 10;
        int gap = 3;

        g.setFont(fontCode);
        FontMetrics codeFm = g.getFontMetrics();
        int codeH = codeFm.getAscent();
        int contentH = logo + gap + codeH;
        int sheetH = contentH + padY * 2;
        int sheetTop = 130 - (int) sheetLift;
        int sheetL = 28;
        int sheetR = SIZE - 28;

        String badge = goalMode ? "LIVE · GOAL" : "LIVE · NO GOAL";
        drawBadge(g, badge, badgeFill, badgeOutline, badgeBg);

        Color border = goalMode
                ? mix(ACCENT, GOLD, 0.15 + 0.55 * scoreAccent)
                : mix(ACCENT, WHITE, 0.1);
        Color fill = goalMode
                ? mix(BG2, mix(ACCENT, GOLD, 0.25 * scoreAccent), 0.22 + 0.08 * scoreGlow)
                : mix(BG2, ACCENT, 0.20);
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Float(sheetL, sheetTop, sheetR - sheetL, sheetH, 12, 12));
        g.setStroke(new BasicStroke(scoreAccent > 0.55 && goalMode ? 2f : 1f));
        g.setColor(mix(border, WHITE, 0.12 + 0.2 * (goalMode ? scoreAccent : 0)));
        g.draw(new RoundRectangle2D.Float(sheetL, sheetTop, sheetR - sheetL, sheetH, 12, 12));

        int contentTop = sheetTop + padY;
        int lx = sheetL + padX;
        int ly = contentTop;
        int rx = sheetR - padX - logo;
        if (homeLogo != null) {
            g.drawImage(homeLogo, lx, ly, logo, logo, null);
        }
        if (awayLogo != null) {
            g.drawImage(awayLogo, rx, ly, logo, logo, null);
        }

        int codeBaseline = ly + logo + gap + codeFm.getAscent();
        drawCenteredCodeAtBaseline(g, homeCode, lx, codeBaseline, logo, mix(TEAM_HOME, WHITE, 0.25));
        drawCenteredCodeAtBaseline(g, awayCode, rx, codeBaseline, logo, mix(TEAM_AWAY, WHITE, 0.25));

        String score = scoreHome + ":" + scoreAway;
        int cy = sheetTop + sheetH / 2;
        drawScore(g, score, SIZE / 2, cy, scoreScale, scoreGlow, scoreAccent, struck, strikeT, goalMode);

        int subBaseline = sheetTop + sheetH + 20;
        if (subtitle != null && !subtitle.isBlank()) {
            g.setFont(fontSub);
            FontMetrics fm = g.getFontMetrics();
            Color subCol = goalMode
                    ? mix(HINT, GOLD, 0.55 * Math.max(scoreAccent, te.alpha * 0.4))
                    : mix(HINT, CANCEL_LIFT, 0.5 * te.alpha);
            g.setColor(subCol);
            int sw = fm.stringWidth(subtitle);
            g.drawString(subtitle, (SIZE - sw) / 2, subBaseline);
        }

        if (te.show && te.alpha > 0.02) {
            // Title sits clearly below the subtitle line
            int titleCenterY = subBaseline + 58;
            drawTitle(g, goalMode ? "ГОЛ!" : "ОТМЕНЁН", te.alpha, te.scale, te.flash, titleA, titleB, titleCenterY);
        }

        g.dispose();
        return img;
    }

    private void drawBadge(Graphics2D g, String text, Color fill, Color outline, Color bg) {
        g.setFont(fontBadge);
        FontMetrics fm = g.getFontMetrics();
        int padX = 12;
        int padY = 6;
        int tw = fm.stringWidth(text);
        int th = fm.getAscent() + fm.getDescent();
        int boxW = tw + padX * 2;
        int boxH = th + padY * 2;
        int bx = (SIZE - boxW) / 2;
        int by = 32;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Float(bx, by, boxW, boxH, 8, 8));
        g.setStroke(new BasicStroke(1f));
        g.setColor(outline);
        g.draw(new RoundRectangle2D.Float(bx, by, boxW, boxH, 8, 8));
        g.setColor(fill);
        int tx = bx + (boxW - tw) / 2;
        int ty = by + (boxH - th) / 2 + fm.getAscent();
        g.drawString(text, tx, ty);
    }

    private void drawCenteredCodeAtBaseline(Graphics2D g, String code, int x, int baseline, int boxW, Color color) {
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(code);
        g.setColor(color);
        g.drawString(code, x + (boxW - tw) / 2, baseline);
    }

    private void drawCenteredCode(Graphics2D g, String code, int x, int y, int boxW, Color color) {
        FontMetrics fm = g.getFontMetrics();
        drawCenteredCodeAtBaseline(g, code, x, y + fm.getAscent(), boxW, color);
    }

    private void drawScore(
            Graphics2D g, String score, int cx, int cy, double scale, double glow, double accent,
            boolean struck, double strikeT, boolean goalMode
    ) {
        float size = Math.max(22f, (float) (40 * scale));
        Font font = fontScore.deriveFont(Font.BOLD, size);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(score);
        int x = cx - tw / 2;
        // Baseline so glyph box is vertically centered on cy
        int y = cy - fm.getHeight() / 2 + fm.getAscent();

        Color lift = goalMode ? mix(GOLD, GOAL_FLASH, 0.55) : CANCEL_LIFT;
        Color fill = mix(WHITE, lift, 0.15 + 0.55 * accent);
        if (struck) {
            fill = mix(fill, HINT, 0.35 + 0.25 * strikeT);
        }
        if (glow > 0.12) {
            Color glowCol = mix(ACCENT, goalMode ? GOLD : CANCEL, 0.25 * accent);
            for (int k : new int[]{6, 3}) {
                g.setColor(withAlpha(glowCol, (int) (40 * glow * (k / 6.0))));
                g.drawString(score, x - k / 2, y - k / 2);
            }
        }
        g.setColor(fill);
        g.drawString(score, x, y);

        if (struck && strikeT > 0.05) {
            int pad = 8;
            int y0 = cy;
            int x0 = x - pad;
            int x1 = x + tw + pad;
            int x1a = (int) lerp(x0, x1, Math.min(1.0, strikeT * 1.2));
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(withAlpha(CANCEL, (int) (220 * Math.min(1, strikeT * 1.4))));
            g.drawLine(x0, y0, x1a, y0);
        }
    }

    private void drawTitle(Graphics2D g, String word, double alpha, double scale, double flash, Color a, Color b, int centerY) {
        double aa = Math.pow(smoothstep(alpha), 0.85);
        float size = Math.max(26f, (float) (46 * scale));
        if (word.length() > 4) {
            size *= 1.02f;
        }
        Font font = fontTitle.deriveFont(Font.BOLD, size);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(word);
        int rise = (int) ((1 - aa) * 10);
        int wx = (SIZE - tw) / 2;
        int wy = centerY - rise - fm.getHeight() / 2 + fm.getAscent();

        Color col = mix(a, b, 0.45 + flash * 0.35);
        // single soft halo — avoid double-draw ghosting
        g.setColor(withAlpha(col, (int) (255 * aa * 0.22)));
        g.drawString(word, wx - 1, wy - 1);
        Color core = mix(b, WHITE, 0.35 + flash * 0.2);
        g.setColor(withAlpha(core, (int) (255 * aa)));
        g.drawString(word, wx, wy);
    }

    private record TitleEnv(boolean show, double alpha, double scale, double flash) {
    }

    private static TitleEnv titleEnvelope(double t) {
        if (t < 0.10) {
            return new TitleEnv(false, 0, 1, 0);
        }
        if (t < 0.30) {
            double u = smoothstep((t - 0.10) / 0.20);
            return new TitleEnv(true, easeOut(u), lerp(0.72, 1.06, easeOut(u)), Math.sin(u * Math.PI) * 0.5);
        }
        if (t < 0.40) {
            double u = (t - 0.30) / 0.10;
            return new TitleEnv(true, 1.0, lerp(1.06, 1.0, easeInOut(u)), 0.06);
        }
        if (t < 0.58) {
            double u = smoothstep((t - 0.40) / 0.18);
            return new TitleEnv(true, lerp(1.0, 0.55, easeIn(u)), 0.98, 0);
        }
        if (t < 0.78) {
            double u = smoothstep((t - 0.58) / 0.20);
            return new TitleEnv(true, lerp(0.55, 0.0, easeIn(u)), lerp(0.98, 0.92, u), 0);
        }
        return new TitleEnv(false, 0, 1, 0);
    }

    private BufferedImage loadLogo(int teamId, String teamCode) {
        try {
            byte[] bytes = null;
            if (teamId > 0) {
                bytes = logoCache.bytesForTeam(teamId);
            }
            if (bytes == null || bytes.length == 0) {
                bytes = fetchEspnLogo(teamCode);
            }
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) {
                return null;
            }
            int logo = 44;
            BufferedImage out = new BufferedImage(logo, logo, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            enableQuality(g);
            g.drawImage(src, 0, 0, logo, logo, null);
            g.dispose();
            return out;
        } catch (Exception e) {
            log.debug("Logo load failed teamId={} code={}: {}", teamId, teamCode, e.getMessage());
            return null;
        }
    }

    private static byte[] fetchEspnLogo(String teamCode) {
        if (teamCode == null || teamCode.isBlank()) {
            return null;
        }
        try {
            String internal = zhigalin.predictions.util.TeamCodeMapper.toInternalCode(teamCode);
            String url = zhigalin.predictions.util.EspnTeamLogos.logoUrl(internal);
            if (url == null || url.isBlank()) {
                return null;
            }
            kong.unirest.HttpResponse<byte[]> resp = kong.unirest.Unirest.get(url)
                    .header("User-Agent", "predictions-bot/1.0")
                    .connectTimeout(2500)
                    .socketTimeout(4000)
                    .asBytes();
            if (resp.getStatus() >= 200 && resp.getStatus() < 300 && resp.getBody() != null && resp.getBody().length > 0) {
                return resp.getBody();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static BufferedImage loadBackground() {
        try (InputStream in = GoalEventGifRenderer.class.getResourceAsStream("/notification-cards/goal-anim-bg.png")) {
            if (in != null) {
                BufferedImage src = ImageIO.read(in);
                if (src != null) {
                    BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = scaled.createGraphics();
                    enableQuality(g);
                    g.drawImage(src, 0, 0, SIZE, SIZE, null);
                    g.dispose();
                    return scaled;
                }
            }
        } catch (Exception ignored) {
        }
        BufferedImage fallback = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = fallback.createGraphics();
        g.setColor(new Color(11, 9, 16));
        g.fillRect(0, 0, SIZE, SIZE);
        g.dispose();
        return fallback;
    }

    private static Font loadFont(float size, boolean bold) {
        String[] paths = bold
                ? new String[]{
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
                }
                : new String[]{
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
                };
        for (String p : paths) {
            try {
                Font f = Font.createFont(Font.TRUETYPE_FONT, Path.of(p).toFile());
                return f.deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
            } catch (Exception ignored) {
            }
        }
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    private static void enableQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private static double easeIn(double t) {
        return t * t * t;
    }

    private static double easeInOut(double t) {
        return 3 * t * t - 2 * t * t * t;
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static Color mix(Color c1, Color c2, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) lerp(c1.getRed(), c2.getRed(), t),
                (int) lerp(c1.getGreen(), c2.getGreen(), t),
                (int) lerp(c1.getBlue(), c2.getBlue(), t)
        );
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
