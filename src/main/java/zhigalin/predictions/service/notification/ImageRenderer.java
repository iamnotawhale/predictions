package zhigalin.predictions.service.notification;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.util.DaoUtil;

import static zhigalin.predictions.service.odds.OddsService.ODDS;
import static zhigalin.predictions.service.odds.OddsService.Odd;
import static zhigalin.predictions.util.ColorComparator.similarTo;

@Service
public class ImageRenderer {

    private static final Logger log = LoggerFactory.getLogger("server");

    // Canvas
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 1024;
    public static final Color BACKGROUND_COLOR = new Color(55, 0, 60);

    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final ObjectMapper objectMapper;
    private final PanicSender panicSender;

    private final Map<String, TeamColor> teamColors = new HashMap<>();
    private final Map<Integer, BufferedImage> teamLogoCache = new ConcurrentHashMap<>();
    private final Map<String, Font> fontCache = new ConcurrentHashMap<>();
    private final OddsService oddsService;
    private volatile BufferedImage plLogo;
    private final Semaphore renderGate = new Semaphore(1);

    public ImageRenderer(MatchService matchService,
                         HeadToHeadService headToHeadService,
                         ObjectMapper objectMapper,
                         PanicSender panicSender, OddsService oddsService) {
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.objectMapper = objectMapper;
        this.panicSender = panicSender;
        this.oddsService = oddsService;
    }

    @PostConstruct
    public void initTeamColors() {
        try (InputStream teamColorsInput = getClass().getClassLoader().getResourceAsStream("team_colors.json")) {
            if (teamColorsInput != null) {
                JsonNode jsonNode = objectMapper.readTree(teamColorsInput);
                jsonNode.fields().forEachRemaining(teamNode -> {
                    String teamId = teamNode.getKey();
                    JsonNode teamColorNode = teamNode.getValue();
                    JsonNode home = teamColorNode.get("home");
                    JsonNode away = teamColorNode.get("away");
                    JsonNode third = teamColorNode.get("third");
                    teamColors.put(
                            teamId,
                            new TeamColor(
                                    new Color(home.get("r").asInt(), home.get("g").asInt(), home.get("b").asInt()),
                                    new Color(away.get("r").asInt(), away.get("g").asInt(), away.get("b").asInt()),
                                    new Color(third.get("r").asInt(), third.get("g").asInt(), third.get("b").asInt())
                            )
                    );
                });
            }
        } catch (Exception e) {
            String message = "Error creating team colors";
            panicSender.sendPanic(message, e);
            log.error("{}: {}", message, e.getMessage());
        }
    }

    public String createTodayMatchesImage(List<MatchRecord> list) {
        return withRenderGate(() -> createTodayMatchesImageUnlocked(list));
    }

    private String createTodayMatchesImageUnlocked(List<MatchRecord> list) {
        Map<Integer, List<MatchRecord>> weeks = list.stream().collect(java.util.stream.Collectors.groupingBy(MatchRecord::weekId));
        int allRows = list.size() + weeks.size();
        int fullSize = allRows * 90;
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();
            int middleX = WIDTH / 2;
            int weekBlockY = (HEIGHT - fullSize) / 2;

            for (Map.Entry<Integer, List<MatchRecord>> entry : weeks.entrySet()) {
                int weekId = entry.getKey();
                List<MatchRecord> matchRecords = entry.getValue();
                int elements = matchRecords.size() + 1;

                BufferedImage weekBlock = new BufferedImage(WIDTH, elements * 90, BufferedImage.TYPE_INT_ARGB);
                Graphics2D wG = weekBlock.createGraphics();
                wG.setColor(Color.WHITE);
                Font font = loadFont(true).deriveFont(50f);
                wG.setFont(font);

                String message = "WEEK " + weekId;
                int weekTextX = middleX - wG.getFontMetrics().stringWidth(message) / 2;
                int weekTextY = wG.getFontMetrics().getHeight();
                wG.drawString(message, weekTextX, weekTextY);

                for (int matchNum = 1; matchNum < elements; matchNum++) {
                    MatchRecord mr = matchRecords.get(matchNum - 1);
                    BufferedImage matchBlock = new BufferedImage((int) (WIDTH * 0.8), 80, BufferedImage.TYPE_INT_ARGB);
                    int h = matchBlock.getHeight();
                    int w = matchBlock.getWidth();

                    BufferedImage home = scaleImage(loadTeamLogo(mr.homeTeamId()), h);
                    BufferedImage away = scaleImage(loadTeamLogo(mr.awayTeamId()), h);

                    Graphics2D bG = matchBlock.createGraphics();
                    BufferedImage fill = generateWithGradient(w - h, h - 20, mr.homeTeamId(), mr.awayTeamId());
                    bG.drawImage(fill, h / 2, 10, null);

                    bG.setPaint(new Color(255, 255, 255, 100));
                    bG.fillRect(w / 2 - 80, 10, 160, fill.getHeight());

                    font = loadFont(false).deriveFont(50f);
                    bG.setColor(Color.WHITE);
                    bG.setFont(font);

                    String time = DateTimeFormatter.ofPattern("HH:mm").format(mr.localDateTime());
                    Rectangle2D tb = bG.getFontMetrics().getStringBounds(time, bG);
                    int timeX = (w - bG.getFontMetrics().stringWidth(time)) / 2;
                    int timeY = (int) ((double) h / 2 - tb.getHeight() / 2 - tb.getY());
                    bG.drawString(time, timeX, timeY);

                    font = loadFont(true).deriveFont(80f);
                    bG.setFont(font);

                    String homeCode = DaoUtil.TEAMS.get(mr.homeTeamId()).getCode();
                    Rectangle2D hb = bG.getFontMetrics().getStringBounds(homeCode, bG);
                    int homeX = w / 2 - 100 - bG.getFontMetrics().stringWidth(homeCode);
                    int homeY = (int) ((double) h / 2 - hb.getHeight() / 2 - hb.getY());
                    bG.drawString(homeCode, homeX, homeY);

                    String awayCode = DaoUtil.TEAMS.get(mr.awayTeamId()).getCode();
                    Rectangle2D ab = bG.getFontMetrics().getStringBounds(awayCode, bG);
                    int awayX = w / 2 + 100;
                    int awayY = (int) ((double) h / 2 - ab.getHeight() / 2 - ab.getY());
                    bG.drawString(awayCode, awayX, awayY);

                    bG.drawImage(home, null, 0, 0);
                    bG.drawImage(away, null, w - h, 0);
                    bG.dispose();

                    wG.drawImage(matchBlock, null, middleX - w / 2, matchNum * 90);
                }
                wG.dispose();
                g2d.drawImage(weekBlock, null, 0, weekBlockY);
                weekBlockY += elements * 90;
            }
            g2d.dispose();
            File temp = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", temp);
            return temp.getAbsolutePath();
        } catch (Exception e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            log.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    public String createImage(Integer matchPublicId,
                              Integer homeTeamId,
                              Integer awayTeamId,
                              String centerInfo,
                              NotificationImageMode mode,
                              List<Result> results) {
        return withRenderGate(() -> createImageUnlocked(matchPublicId, homeTeamId, awayTeamId, centerInfo, mode, results));
    }

    private String createImageUnlocked(Integer matchPublicId,
                                       Integer homeTeamId,
                                       Integer awayTeamId,
                                       String centerInfo,
                                       NotificationImageMode mode,
                                       List<Result> results) {
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();

            BufferedImage matchBlock = new BufferedImage((int) (WIDTH * 0.90), 200, BufferedImage.TYPE_INT_ARGB);
            int h = matchBlock.getHeight();
            int w = matchBlock.getWidth();

            BufferedImage homePic = scaleImage(loadTeamLogo(homeTeamId), h);
            BufferedImage awayPic = scaleImage(loadTeamLogo(awayTeamId), h);

            Graphics2D bG = matchBlock.createGraphics();
            BufferedImage fill = generateWithGradient(w - h, (int) (h * 0.6), homeTeamId, awayTeamId);
            bG.drawImage(fill, (w - fill.getWidth()) / 2, (h - fill.getHeight()) / 2, null);

            bG.setPaint(new Color(255, 255, 255, 50));
            bG.fillRect(w / 2 - 100, (h - fill.getHeight()) / 2, 200, fill.getHeight());

            bG.drawImage(homePic, null, 0, 0);
            bG.drawImage(awayPic, null, w - h, 0);

            bG.setColor(Color.WHITE);
            Font font = loadFont(false).deriveFont(60f);
            bG.setFont(font);

            Rectangle2D centerBounds = bG.getFontMetrics().getStringBounds(centerInfo, bG);
            int infoX = (w - bG.getFontMetrics().stringWidth(centerInfo)) / 2;
            int infoY = (int) ((double) h / 2 - centerBounds.getHeight() / 2 - centerBounds.getY());
            bG.drawString(centerInfo, infoX, infoY);

            font = loadFont(true).deriveFont(80f);
            bG.setFont(font);

            String homeCode = DaoUtil.TEAMS.get(homeTeamId).getCode();
            Rectangle2D hb = bG.getFontMetrics().getStringBounds(homeCode, bG);
            int homeX = w / 2 - 120 - bG.getFontMetrics().stringWidth(homeCode);
            int homeY = (int) ((double) h / 2 - hb.getHeight() / 2 - hb.getY());
            bG.drawString(homeCode, homeX, homeY);

            String awayCode = DaoUtil.TEAMS.get(awayTeamId).getCode();
            Rectangle2D ab = bG.getFontMetrics().getStringBounds(awayCode, bG);
            int awayX = w / 2 + 120;
            int awayY = (int) ((double) h / 2 - ab.getHeight() / 2 - ab.getY());
            bG.drawString(awayCode, awayX, awayY);

            bG.dispose();
            g2d.drawImage(matchBlock, (WIDTH - w) / 2, (HEIGHT - h) / 2, null);

            int middleY = image.getHeight() / 2;

            switch (mode) {
                case NOTIFICATION -> {
                    Odd odd = ODDS.getOrDefault(matchPublicId, null);
                    List<Match> homeTeamLast = matchService.findLast5MatchesByTeamId(homeTeamId);
                    List<Match> awayTeamLast = matchService.findLast5MatchesByTeamId(awayTeamId);
                    List<HeadToHead> h2h = headToHeadService.findAllByTwoTeamsCode(
                            DaoUtil.TEAMS.get(homeTeamId).getCode(),
                            DaoUtil.TEAMS.get(awayTeamId).getCode()
                    );

                    int picSize = 40;
                    drawHeadToHead(g2d, h2h, homeTeamId, awayTeamId, picSize);
                    drawLastMatchesInfo(g2d, homeTeamLast, homeTeamId, picSize, true);
                    drawLastMatchesInfo(g2d, awayTeamLast, awayTeamId, picSize, false);

                    font = loadFont(false).deriveFont(30f * ((float) picSize / 40));
                    g2d.setFont(font);
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                    String info = "last 6";
                    Rectangle2D bounds = g2d.getFontMetrics().getStringBounds(info, g2d);
                    int infoY2 = (int) ((double) HEIGHT / 2 + 130 - bounds.getHeight() / 2 - bounds.getY()) + (picSize * 2 + 10) / 2;
                    g2d.drawString(info, (WIDTH - g2d.getFontMetrics().stringWidth(info)) / 2, infoY2);

                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                    font = loadFont(false).deriveFont(40f);
                    g2d.setFont(font);

                    if (odd == null) {
                        oddsService.oddsInit2(List.of(matchService.findByTeamIds(homeTeamId, awayTeamId)));
                        odd = ODDS.getOrDefault(matchPublicId, null);
                    }

                    if (odd != null) {
                        Double homeOdd = odd.home();
                        int tx3 = WIDTH / 4 - (g2d.getFontMetrics().stringWidth(String.valueOf(homeOdd)) / 2);
                        int ty = middleY + 400;
                        g2d.drawString(String.valueOf(homeOdd), tx3, ty);

                        Double drawOdd = odd.draw();
                        int tx4 = (WIDTH / 2) - (g2d.getFontMetrics().stringWidth(String.valueOf(drawOdd)) / 2);
                        g2d.drawString(String.valueOf(drawOdd), tx4, ty);

                        Double awayOdd = odd.away();
                        int tx5 = WIDTH * 3 / 4 - (g2d.getFontMetrics().stringWidth(String.valueOf(awayOdd)) / 2);
                        g2d.drawString(String.valueOf(awayOdd), tx5, ty);

                        g2d.setColor(new Color(255, 255, 255, 50));
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int rectWidth = 160, rectHeight = 80, arc = 40;

                        String win1 = "1";
                        int win1X = WIDTH / 4 - (g2d.getFontMetrics().stringWidth(win1) / 2);
                        int winY = middleY + 320;
                        g2d.drawString(win1, win1X, winY);

                        String win2 = "2";
                        int win2X = WIDTH * 3 / 4 - (g2d.getFontMetrics().stringWidth(win2) / 2);
                        g2d.drawString(win2, win2X, winY);

                        String draw = "X";
                        int drawX = (WIDTH / 2) - (g2d.getFontMetrics().stringWidth(draw) / 2);
                        g2d.drawString(draw, drawX, winY);

                        g2d.fillRoundRect(WIDTH / 4 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arc, arc);
                        g2d.fillRoundRect(WIDTH / 2 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arc, arc);
                        g2d.fillRoundRect(WIDTH * 3 / 4 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arc, arc);
                    }
                }
                case YOUR_PREDICT -> {
                    font = loadFont(false).deriveFont(50f);
                    g2d.setFont(font);
                    Match match = matchService.findByPublicId(matchPublicId);
                    String time = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());

                    String week = "WEEK " + match.getWeekId();
                    int textX = WIDTH / 2 - (g2d.getFontMetrics().stringWidth(week) / 2);
                    int textY = middleY - 240;
                    g2d.drawString(week, textX, textY);

                    int timeX = WIDTH / 2 - (g2d.getFontMetrics().stringWidth(time) / 2);
                    int timeY = middleY - 180;
                    g2d.drawString(time, timeX, timeY);

                    font = new Font("Arial", Font.BOLD, 50);
                    g2d.setFont(font);
                    String message = "ТВОЙ ПРОГНОЗ";
                    int msgX = (WIDTH / 2) - (g2d.getFontMetrics().stringWidth(message) / 2);
                    int msgY = middleY + 240;
                    g2d.drawString(message, msgX, msgY);
                }
                case RESULT -> {
                    BufferedImage resultImage = new BufferedImage(WIDTH / 2, HEIGHT / 6, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D rG = resultImage.createGraphics();

                    int bw = resultImage.getWidth(), bh = resultImage.getHeight();
                    int midY = bh / 2, midX = bw / 2;
                    int offY = (int) (bh * 0.1), offX = (int) (bw * 0.05);

                    rG.setColor(new Color(255, 255, 255, 30));
                    rG.fillRoundRect(0, 0, bw, bh, 20, 20);

                    rG.setColor(Color.WHITE);
                    Font f = loadFont(false).deriveFont(32f);
                    rG.setFont(f);

                    for (int i = 0; i < Math.min(4, results != null ? results.size() : 0); i++) {
                        String line = results.get(i).login() + " " + results.get(i).predict() + " [" + results.get(i).point() + "]";
                        int w0 = rG.getFontMetrics().stringWidth(line);
                        if (i == 0) {
                            rG.drawString(line, midX - w0 - offX, midY - offY * 2);
                        } else if (i == 1) {
                            rG.drawString(line, midX + offX, midY - offY * 2);
                        } else if (i == 2) {
                            rG.drawString(line, midX - w0 - offX, midY + offY * 3);
                        } else {
                            rG.drawString(line, midX + offX, midY + offY * 3);
                        }
                    }
                    rG.dispose();
                    g2d.drawImage(resultImage, (WIDTH - resultImage.getWidth()) / 2, middleY + 150, null);
                }
            }

            g2d.dispose();
            File temp = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", temp);
            return temp.getAbsolutePath();
        } catch (Exception e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            log.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    public String createWeeklyImage(int weekId, Map<String, Integer> usersPoints) {
        return withRenderGate(() -> createWeeklyImageUnlocked(weekId, usersPoints));
    }

    private String createWeeklyImageUnlocked(int weekId, Map<String, Integer> usersPoints) {
        int y = (HEIGHT - usersPoints.size() * 60) / 2;
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();
            int midX = WIDTH / 2;

            final int[] blockY = {y};
            final int[] place = {1};

            usersPoints.forEach((login, pts) -> {
                BufferedImage stBlock = new BufferedImage((int) (WIDTH * 0.7), 90, BufferedImage.TYPE_INT_ARGB);
                Graphics2D st = stBlock.createGraphics();
                Color color = new Color(255, 255, 255, 80);
                Color placeColor = switch (place[0]) {
                    case 1 -> new Color(134, 0, 125, 240);
                    case 2 -> new Color(134, 0, 125, 160);
                    case 3 -> new Color(134, 0, 125, 80);
                    default -> new Color(255, 255, 255, 0);
                };

                st.setPaint(placeColor);
                st.fillRect(0, 0, (int) (stBlock.getWidth() * 0.15), 90);
                st.setPaint(color);
                st.fillRect((int) (stBlock.getWidth() * 0.15), 0, stBlock.getWidth() - (int) (stBlock.getWidth() * 0.15), 90);
                st.setPaint(Color.WHITE);

                Font font = loadFont(true).deriveFont(70f);
                st.setFont(font);

                var fm = st.getFontMetrics();

                String plc = String.valueOf(place[0]++);
                Rectangle2D plcBounds = fm.getStringBounds(plc, st);
                int plcX = ((int) (stBlock.getWidth() * 0.15) - fm.stringWidth(plc)) / 2;
                int plcY = (int) ((double) stBlock.getHeight() / 2 - plcBounds.getHeight() / 2 - plcBounds.getY());
                st.drawString(plc, plcX, plcY);

                String lgn = login.toUpperCase().substring(0, 3);
                Rectangle2D loginBounds = fm.getStringBounds(lgn, st);
                int lgnX = (int) (stBlock.getWidth() * 0.25);
                int lgnY = (int) ((double) stBlock.getHeight() / 2 - loginBounds.getHeight() / 2 - loginBounds.getY());
                st.drawString(lgn, lgnX, lgnY);

                Rectangle2D ptsBounds = fm.getStringBounds(String.valueOf(pts), st);
                int ptsX = (int) (stBlock.getWidth() * 0.95) - fm.stringWidth(String.valueOf(pts));
                int ptsY = (int) ((double) stBlock.getHeight() / 2 - ptsBounds.getHeight() / 2 - ptsBounds.getY());
                st.drawString(String.valueOf(pts), ptsX, ptsY);

                st.dispose();
                g2d.drawImage(stBlock, midX - stBlock.getWidth() / 2, blockY[0], null);
                blockY[0] += 110;
            });

            g2d.setPaint(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60));
            var fm = g2d.getFontMetrics();
            String title = "РЕЗУЛЬТАТЫ " + weekId + " ТУРА";
            Rectangle2D ttlBounds = fm.getStringBounds(title, g2d);
            int ttlX = (WIDTH - fm.stringWidth(title)) / 2;
            int ttlY = (int) (y - 40 - ttlBounds.getHeight());
            g2d.drawString(title, ttlX, ttlY);

            g2d.dispose();
            File temp = File.createTempFile("weekly", ".png");
            ImageIO.write(image, "png", temp);
            return temp.getAbsolutePath();
        } catch (Exception e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            log.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    public BufferedImage generateWithBackground(int width, int height, Color color) throws Exception {
        int stripeWidth = 20;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, color, width, 0, color);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);

        BufferedImage stripe = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D stripeG2d = stripe.createGraphics();

        int sWidth = stripe.getWidth();
        int sHeight = stripe.getHeight();

        stripeG2d.setColor(new Color(255, 255, 255, 30));
        for (int i = 0; i < sWidth; i += stripeWidth) {
            stripeG2d.drawLine(i, 0, i + sHeight, sHeight);
        }
        for (int i = stripeWidth; i < sHeight; i += stripeWidth) {
            stripeG2d.drawLine(0, i, sWidth - i, sHeight);
        }
        stripeG2d.dispose();

        g2d.drawImage(stripe, (image.getWidth() - sWidth) / 2, (image.getHeight() - sHeight) / 2, null);

        BufferedImage logo = loadPlLogo();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.04f));
        g2d.drawImage(logo, -400, -150, null);

        g2d.dispose();
        return image;
    }

    public BufferedImage generateWithGradient(int width, int height, int homeTeamId, int awayTeamId) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        TeamColor homePalette = resolveTeamColor(homeTeamId);
        TeamColor awayPalette = resolveTeamColor(awayTeamId);
        Color home = homePalette.home();
        Color away = awayPalette.away();
        if (similarTo(home, away)) {
            away = awayPalette.third();
        }
        Graphics2D g2d = image.createGraphics();
        GradientPaint gradient = new GradientPaint(0, 0, home, width, 0, away);
        g2d.setPaint(gradient);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        return image;
    }

    private TeamColor resolveTeamColor(int teamId) {
        TeamColor existing = teamColors.get(String.valueOf(teamId));
        if (existing != null) {
            return existing;
        }

        // Fallback for newly promoted/updated teams that are absent in team_colors.json.
        int hueBase = Math.floorMod(teamId * 37, 360);
        Color home = Color.getHSBColor(hueBase / 360f, 0.70f, 0.85f);
        Color away = Color.getHSBColor(((hueBase + 140) % 360) / 360f, 0.60f, 0.80f);
        Color third = Color.getHSBColor(((hueBase + 260) % 360) / 360f, 0.55f, 0.75f);
        TeamColor generated = new TeamColor(home, away, third);
        teamColors.put(String.valueOf(teamId), generated);
        log.warn("Fallback team colors generated for team id={}", teamId);
        return generated;
    }

    public static BufferedImage scaleImage(BufferedImage image, int height) {
        BufferedImage scaled = new BufferedImage(height, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int srcW = image.getWidth();
        int srcH = image.getHeight();
        double ratio = Math.min((double) height / srcW, (double) height / srcH);
        int drawW = (int) Math.round(srcW * ratio);
        int drawH = (int) Math.round(srcH * ratio);
        int x = (height - drawW) / 2;
        int y = (height - drawH) / 2;

        g2d.drawImage(image, x, y, drawW, drawH, null);
        g2d.dispose();
        return scaled;
    }

    private BufferedImage grayScaling(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xff;
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;
            int avg = (r + g + b) / 3;
            pixels[i] = (a << 24) | (avg << 16) | (avg << 8) | avg;
        }
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    private void drawHeadToHead(Graphics2D g2d, List<HeadToHead> h2h, Integer homeTeamId, Integer awayTeamId, int picSize) throws Exception {
        Map<Integer, BufferedImage> pics = Map.of(
                homeTeamId, grayScaling(scaleImage(loadTeamLogo(homeTeamId), picSize)),
                awayTeamId, grayScaling(scaleImage(loadTeamLogo(awayTeamId), picSize))
        );

        int h2hNum = h2h.size();
        BufferedImage h2hBlock = new BufferedImage((picSize * 3 + 10) * 4, (picSize + 10) * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D h2hG = h2hBlock.createGraphics();
        int x = h2hNum <= 6 ? picSize * 3 / 2 + 10 : 0;
        int y = picSize + 10;
        int h2hCount = 1;

        for (HeadToHead item : h2h) {
            BufferedImage matchBlock = new BufferedImage(picSize * 3, picSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D mG = matchBlock.createGraphics();
            Font font = loadFont(false).deriveFont(28f * ((float) picSize / 40));
            mG.setFont(font);
            mG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));

            mG.drawImage(pics.get(item.getHomeTeamId()), 0, 0, null);
            mG.drawImage(pics.get(item.getAwayTeamId()), matchBlock.getWidth() - pics.get(item.getAwayTeamId()).getWidth(), 0, null);

            String result = item.getHomeTeamScore() + ":" + item.getAwayTeamScore();
            var fm = mG.getFontMetrics();
            Rectangle2D cb = fm.getStringBounds(result, mG);
            int rx = (matchBlock.getWidth() - fm.stringWidth(result)) / 2;
            int ry = (int) ((double) matchBlock.getHeight() / 2 - cb.getHeight() / 2 - cb.getY());
            mG.drawString(result, rx, ry);
            mG.dispose();

            h2hG.drawImage(matchBlock, x, y, null);
            if ((h2hNum <= 6 && h2hCount == 3) || (h2hNum > 6 && h2hCount == 4)) {
                x = (h2hBlock.getWidth() - (picSize * 3 + 10) * (h2hNum - h2hCount) + 10) / 2;
                y += picSize + 10;
            } else {
                x += matchBlock.getWidth() + 10;
            }
            h2hCount++;
        }

        String type = "head to head";
        Font font = loadFont(false).deriveFont(30f * ((float) picSize / 40));
        h2hG.setFont(font);
        h2hG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        int tX = (h2hBlock.getWidth() - h2hG.getFontMetrics().stringWidth(type)) / 2;
        h2hG.drawString(type, tX, picSize - 10);
        h2hG.dispose();

        g2d.drawImage(h2hBlock, (WIDTH - h2hBlock.getWidth()) / 2, HEIGHT / 2 - 280 - (picSize - 40) * 3, null);
    }

    private void drawLastMatchesInfo(Graphics2D g2d, List<Match> matches, int teamId, int picSize, boolean isHome) throws Exception {
        int x = 0, y = 0, matchNum = 1;
        BufferedImage block = new BufferedImage((picSize * 2 + 10) * 3, picSize * 2 + 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mBg = block.createGraphics();
        for (Match m : matches) {
            int againstTeamCode = m.getHomeTeamId() == teamId ? m.getAwayTeamId() : m.getHomeTeamId();
            BufferedImage againstTeamPic = grayScaling(scaleImage(loadTeamLogo(againstTeamCode), picSize));

            BufferedImage mBlock = new BufferedImage(picSize * 2, picSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D mG = mBlock.createGraphics();
            Font font = loadFont(false).deriveFont(28f * ((float) picSize / 40));
            mG.setFont(font);
            mG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));

            String result = m.getHomeTeamId() == teamId ? m.getHomeTeamScore() + ":" + m.getAwayTeamScore()
                    : m.getAwayTeamScore() + ":" + m.getHomeTeamScore();

            var fm = mG.getFontMetrics();
            Rectangle2D cb = fm.getStringBounds(result, mG);
            int infoY = (int) ((double) mBlock.getHeight() / 2 - cb.getHeight() / 2 - cb.getY());
            mG.drawString(result, picSize, infoY);
            mG.drawImage(againstTeamPic, 0, 0, null);
            mG.dispose();

            mBg.drawImage(mBlock, x, y, null);
            if (matchNum == 3) {
                x = 0;
                y += mBlock.getHeight() + 10;
            } else {
                x += mBlock.getWidth() + 10;
            }
            matchNum++;
        }
        mBg.dispose();
        if (isHome) {
            g2d.drawImage(block, WIDTH / 2 - 60 - block.getWidth(), HEIGHT / 2 + 130, null);
        } else {
            g2d.drawImage(block, WIDTH / 2 + 60, HEIGHT / 2 + 130, null);
        }
    }

    private Font loadFont(boolean condensed) {
        String key = condensed ? "condensed" : "bold";
        return fontCache.computeIfAbsent(key, k -> {
            try {
                String file = "pl-" + (condensed ? "cond" : "") + "bold.ttf";
                return Font.createFont(Font.TRUETYPE_FONT, new ClassPathResource("static/" + file).getInputStream());
            } catch (Exception e) {
                log.error("Error loading font: {}", e.getMessage());
                return new Font("Arial", Font.BOLD, 30);
            }
        });
    }

    private String withRenderGate(java.util.function.Supplier<String> render) {
        boolean acquired = false;
        try {
            acquired = renderGate.tryAcquire(45, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Image render skipped: gate busy");
                return null;
            }
            return render.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image render interrupted");
            return null;
        } finally {
            if (acquired) {
                renderGate.release();
            }
        }
    }

    private BufferedImage loadTeamLogo(int teamId) throws Exception {
        return teamLogoCache.computeIfAbsent(teamId, id -> {
            try {
                return ImageIO.read(new ClassPathResource("static/img/teams/" + id + ".webp").getInputStream());
            } catch (Exception e) {
                log.error("Error loading team logo {}: {}", id, e.getMessage());
                BufferedImage stub = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = stub.createGraphics();
                g.setPaint(Color.DARK_GRAY);
                g.fillRect(0, 0, 64, 64);
                g.dispose();
                return stub;
            }
        });
    }

    private BufferedImage loadPlLogo() {
        if (plLogo == null) {
            synchronized (this) {
                if (plLogo == null) {
                    try {
                        var resource = new ClassPathResource("static/img/pl_logo.webp");
                        if (resource.exists()) {
                            plLogo = scaleImage(ImageIO.read(resource.getInputStream()), 1600);
                        } else {
                            log.warn("PL logo not found on classpath, using placeholder");
                            plLogo = createLogoPlaceholder(1600);
                        }
                    } catch (Exception e) {
                        log.error("Error loading PL logo: {}", e.getMessage());
                        plLogo = createLogoPlaceholder(1600);
                    }
                }
            }
        }
        return plLogo;
    }

    private static BufferedImage createLogoPlaceholder(int size) {
        BufferedImage stub = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = stub.createGraphics();
        g.setPaint(new Color(255, 255, 255, 20));
        g.fillOval(0, 0, size, size);
        g.dispose();
        return stub;
    }

    private record TeamColor(Color home, Color away, Color third) {
    }
}
