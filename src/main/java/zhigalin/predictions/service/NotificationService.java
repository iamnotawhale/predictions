package zhigalin.predictions.service;

import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.MultipartBody;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.util.DaoUtil;

import static zhigalin.predictions.service.odds.OddsService.ODDS;
import static zhigalin.predictions.service.odds.OddsService.Odd;
import static zhigalin.predictions.util.ColorComparator.similarTo;

@Service
public class NotificationService {
    @Value("${bot.urlMessage}")
    private String urlMessage;
    @Value("${bot.urlPhoto}")
    private String urlPhoto;
    @Value("${bot.chatId}")
    private String chatId;

    private final PredictionService predictionService;
    private final PanicSender panicSender;
    private final OddsService oddsService;
    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final ObjectMapper objectMapper;
    private final Map<String, TeamColor> teamColors = new HashMap<>();
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    private final Map<Integer, List<String>> notificationBLackList = new HashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private static final int WIDTH = 1024;
    private static final int HEIGHT = 1024;
    private static final Color BACKGROUND_COLOR = new Color(44, 0, 48);

    public NotificationService(
            MatchService matchService, PredictionService predictionService,
            ObjectMapper objectMapper, PanicSender panicSender, OddsService oddsService, HeadToHeadService headToHeadService
    ) {
        this.matchService = matchService;
        this.objectMapper = objectMapper;
        this.headToHeadService = headToHeadService;
        this.predictionService = predictionService;
        this.panicSender = panicSender;
        this.oddsService = oddsService;
        notificationBLackList.put(30, new ArrayList<>());
        notificationBLackList.put(90, new ArrayList<>());
    }

    //    @Scheduled(initialDelay = 1000, fixedDelay = 60000000)
    @Scheduled(cron = "0 0 9 * * *")
    private void sendTodayMatchNotification() {
        List<Match> todayMatches = matchService.findAllByTodayDate();
        if (!todayMatches.isEmpty()) {
            oddsService.oddsInit(todayMatches);

            List<MatchRecord> list = todayMatches.stream()
                    .map(match -> new MatchRecord(
                                    match.getHomeTeamId(),
                                    match.getAwayTeamId(),
                                    match.getWeekId(),
                                    match.getLocalDateTime()
                            )
                    )
                    .sorted(Comparator.comparingInt(MatchRecord::weekId).thenComparing(MatchRecord::localDateTime))
                    .toList();

            MultipartBody body = Unirest.post(urlPhoto)
                    .headers(Map.of("accept", "application/json",
                                    "content-type", "application/json"
                            )
                    )
                    .queryString("chat_id", chatId)
                    .queryString("caption", "Сегодняшние матчи")
                    .field("photo", new File(Objects.requireNonNull(
                            createTodayMatchesImage(list)
                    )));
            HttpResponse<String> response = body.asString();
            if (response.getStatus() == 200) {
                serverLogger.info("Message today's match notification has been send");
            } else {
                serverLogger.warn("Don't send today's match notification. Reason: {}", response.getBody());
            }
        }
    }

    public void check() throws UnirestException, IOException {
        for (Integer minutes : List.of(90, 30)) {
            List<User> users = DaoUtil.USERS.values().stream().toList();
            List<Match> nearest = matchService.findAllNearest(minutes).stream()
                    .filter(match -> !match.getStatus().equals("pst"))
                    .toList();
            if (!nearest.isEmpty()) {
                serverLogger.info("Nearest matches found");
                for (User user : users) {
                    for (Match match : nearest) {
                        boolean hasPredict = predictionService.getByMatchPublicId(match.getPublicId()).stream()
                                .anyMatch(prediction -> prediction.getUserId() == user.getId());
                        if (!hasPredict) {
                            Notification notification = Notification.builder().user(user).match(match).build();
                            if (!notificationBLackList.get(minutes).contains(notification.toString())) {
                                notificationBLackList.get(minutes).add(notification.toString());
                                sendNotification(notification);
                            }
                        }
                    }
                }
            } else {
                notificationBLackList.get(minutes).clear();
            }
        }
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void fullTimeMatchNotification() {
        if (isProcessing.compareAndSet(false, true)) {
            executorService.submit(() -> {
                try {
                    matchService.listenForMatchUpdates();
                    List<Match> matches = matchService.processBatch();
                    if (!matches.isEmpty()) {
                        matches.forEach(this::fullTimeMatchNotification);
                    }
                } finally {
                    isProcessing.set(false);
                }
            });
        } else {
            serverLogger.warn("Skipping execution: previous task still running");
        }
    }

    private String createTodayMatchesImage(List<MatchRecord> list) {
        Map<Integer, List<MatchRecord>> weeksMatchRecords = list.stream()
                .collect(Collectors.groupingBy(MatchRecord::weekId));
        int allRows = list.size() + weeksMatchRecords.size();
        int fullSize = allRows * 90;
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();

            int middleX = WIDTH / 2;
            int weekBlockY = (HEIGHT - fullSize) / 2;
            for (Map.Entry<Integer, List<MatchRecord>> entry : weeksMatchRecords.entrySet()) {
                int weekId = entry.getKey();
                List<MatchRecord> matchRecords = entry.getValue();
                int matchCount = matchRecords.size();
                int elementsCount = matchCount + 1;

                BufferedImage weekBlock = new BufferedImage(WIDTH, elementsCount * 90, BufferedImage.TYPE_INT_ARGB);
                Graphics2D wBGraphics = weekBlock.createGraphics();

                wBGraphics.setColor(Color.WHITE);
                Font font = loadFontFromFile(false).deriveFont(50f);
                wBGraphics.setFont(font);

                String message = "WEEK " + weekId;
                int weekTextWidth = wBGraphics.getFontMetrics().stringWidth(message);
                int weekTextX = middleX - weekTextWidth / 2;
                int weekTextY = wBGraphics.getFontMetrics().getHeight();
                wBGraphics.drawString(message, weekTextX, weekTextY);

                for (int matchNum = 1; matchNum < elementsCount; matchNum++) {
                    MatchRecord matchRecord = matchRecords.get(matchNum - 1);

                    BufferedImage matchBlock = new BufferedImage((int) (WIDTH * 0.8), 80, BufferedImage.TYPE_INT_ARGB);
                    int matchBlockHeight = matchBlock.getHeight();
                    int matchBlockWidth = matchBlock.getWidth();

                    BufferedImage homeTeamPic = scaleImage(ImageIO.read(new ClassPathResource("static/img/teams/" + matchRecord.homeTeamId + ".webp").getInputStream()), matchBlockHeight);
                    BufferedImage awayTeamPic = scaleImage(ImageIO.read(new ClassPathResource("static/img/teams/" + matchRecord.awayTeamId + ".webp").getInputStream()), matchBlockHeight);

                    Graphics2D blockG2d = matchBlock.createGraphics();
//                    this
                    BufferedImage fill = generateWithGradient(matchBlockWidth - matchBlockHeight, matchBlockHeight - 20, matchRecord.homeTeamId, matchRecord.awayTeamId);
                    blockG2d.drawImage(fill, matchBlockHeight / 2, 10, null);
//                    or this
//                    blockG2d.setPaint(new Color(255, 255, 255, 30));
//                    blockG2d.fillRect(matchBlockHeight / 2, 10, matchBlockWidth - matchBlockHeight, matchBlockHeight - 20);

                    blockG2d.setPaint(new Color(255, 255, 255, 100));
                    blockG2d.fillRect(matchBlockWidth / 2 - 80, 10, 160, fill.getHeight());

                    font = loadFontFromFile(false).deriveFont(50f);
                    blockG2d.setColor(Color.WHITE);
                    blockG2d.setFont(font);
                    FontMetrics fontMetrics = blockG2d.getFontMetrics();

                    String time = DateTimeFormatter.ofPattern("HH:mm").format(matchRecord.localDateTime);
                    Rectangle2D timeBounds = fontMetrics.getStringBounds(time, blockG2d);
                    int timeWidth = fontMetrics.stringWidth(time);
                    int timeX = (matchBlockWidth - timeWidth) / 2;
                    int timeY = (int) ((double) matchBlockHeight / 2 - timeBounds.getHeight() / 2 - timeBounds.getY());
                    blockG2d.drawString(time, timeX, timeY);

                    font = loadFontFromFile(true).deriveFont(80f);
                    blockG2d.setFont(font);
                    fontMetrics = blockG2d.getFontMetrics();

                    String homeTeamCode = DaoUtil.TEAMS.get(matchRecord.homeTeamId).getCode();
                    Rectangle2D homeBounds = fontMetrics.getStringBounds(homeTeamCode, blockG2d);
                    int text1Width = fontMetrics.stringWidth(homeTeamCode);
                    int homeX = matchBlockWidth / 2 - 100 - text1Width;
                    int homeY = (int) ((double) matchBlockHeight / 2 - homeBounds.getHeight() / 2 - homeBounds.getY());
                    blockG2d.drawString(homeTeamCode, homeX, homeY);

                    String awayTeamCode = DaoUtil.TEAMS.get(matchRecord.awayTeamId).getCode();
                    Rectangle2D awayBounds = fontMetrics.getStringBounds(awayTeamCode, blockG2d);
                    int awayX = matchBlockWidth / 2 + 100;
                    int awayY = (int) ((double) matchBlockHeight / 2 - awayBounds.getHeight() / 2 - awayBounds.getY());
                    blockG2d.drawString(awayTeamCode, awayX, awayY);

                    blockG2d.drawImage(homeTeamPic, null, 0, 0);
                    blockG2d.drawImage(awayTeamPic, null, matchBlockWidth - matchBlockHeight, 0);
                    blockG2d.dispose();

                    wBGraphics.drawImage(matchBlock, null, middleX - matchBlockWidth / 2, matchNum * 90);
                }
                wBGraphics.dispose();
                g2d.drawImage(weekBlock, null, 0, weekBlockY);
                weekBlockY += elementsCount * 90;
            }

            g2d.dispose();

            File tempFile = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", tempFile);

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    public static BufferedImage scaleImage(BufferedImage image, int height) {
        BufferedImage scaledImage = new BufferedImage(height, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(image, 0, 0, height, height, null);
        g2d.dispose();
        return scaledImage;
    }

    public void fullTimeMatchNotification(Match match) {
        String centerInfo;
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
        predictionService.updateByMatch(match);

        centerInfo = match.getHomeTeamScore() + ":" + match.getAwayTeamScore();
        List<Prediction> predictions = predictionService.getByMatchPublicId(match.getPublicId());
        predictions.sort(Comparator.comparingInt(Prediction::getPoints).reversed());

        List<Result> results = predictions.stream()
                .map(prediction -> {
                    int userId = prediction.getUserId();
                    User user = DaoUtil.USERS.get(userId);
                    String predict = String.join("",
                            prediction.getHomeTeamScore() != null ? String.valueOf(prediction.getHomeTeamScore()) : "",
                            ":",
                            prediction.getAwayTeamScore() != null ? String.valueOf(prediction.getAwayTeamScore()) : ""
                    );
                    return new Result(user.getLogin().substring(0, 3), predict, prediction.getPoints());
                })
                .sorted(Comparator.comparingInt(Result::point).reversed().thenComparing(Result::login))
                .toList();

        MultipartBody body = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .queryString("caption", "Матч " + homeTeam.getCode() + "-" + awayTeam.getCode() + " окончен")
                .field("photo", new File(Objects.requireNonNull(
                        createImage(
                                match.getPublicId(),
                                match.getHomeTeamId(),
                                match.getAwayTeamId(),
                                centerInfo,
                                "result",
                                results
                        )
                )));
        HttpResponse<String> response = body.asString();
        if (response.getStatus() == 200) {
            serverLogger.info("Send results for match {}", homeTeam.getCode() + " " + awayTeam.getCode());
        } else {
            serverLogger.warn("Don't send results for match {}", homeTeam.getCode() + " " + awayTeam.getCode());
        }

    }

    private void totalPointsChart() {
        try {
            MultipartBody body = Unirest.post(urlPhoto)
                    .headers(Map.of("accept", "application/json",
                                    "content-type", "application/json"
                            )
                    )
                    .queryString("chat_id", chatId)
                    .field("photo", new File(Objects.requireNonNull(createChartImage())));
            HttpResponse<String> response = body.asString();
            if (response.getStatus() == 200) {
                serverLogger.info("Message weekly results notification has been send");
            } else {
                serverLogger.warn("Don't send weekly results notification");
            }
        } catch (UnirestException e) {
            String message = "Sending chart notification message error";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
        }
    }

    private String createChartImage() {
        try {
            Map<String, Map<Integer, Integer>> allUsersData = predictionService.getAllUsersCumulativePoints().entrySet().stream()
                    .sorted((entry1, entry2) -> {
                        int maxValue1 = Collections.max(entry1.getValue().values());
                        int maxValue2 = Collections.max(entry2.getValue().values());
                        return Integer.compare(maxValue2, maxValue1);
                    })
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            for (Map.Entry<String, Map<Integer, Integer>> userEntry : allUsersData.entrySet()) {
                String login = userEntry.getKey();
                for (Map.Entry<Integer, Integer> weekEntry : userEntry.getValue().entrySet()) {
                    dataset.addValue(weekEntry.getValue(), login.toUpperCase().substring(0, 3), weekEntry.getKey());
                }
            }

            JFreeChart chart = ChartFactory.createLineChart(
                    "ГРАФИК НАБОРА ОЧКОВ ПО ТУРАМ", "Week", "PTS",
                    dataset, PlotOrientation.VERTICAL, true, true, false);

            chart.setBackgroundPaint(new Color(0, 0, 0, 0));
            chart.setBackgroundImageAlpha(0f);

            CategoryPlot plot = chart.getCategoryPlot();

            plot.setBackgroundPaint(new Color(255, 255, 255, 160));
            plot.setOutlineVisible(false);
            plot.setDomainGridlinePaint(Color.WHITE);
            plot.setRangeGridlinePaint(Color.WHITE);

            plot.getDomainAxis().setLabelPaint(Color.WHITE);
            plot.getDomainAxis().setTickLabelPaint(Color.WHITE);
            plot.getRangeAxis().setLabelPaint(Color.WHITE);
            plot.getRangeAxis().setTickLabelPaint(Color.WHITE);

            plot.setDomainGridlinesVisible(true);
            plot.setRangeGridlinesVisible(true);

            LineAndShapeRenderer renderer = new LineAndShapeRenderer();
            for (int i = 0; i < dataset.getRowCount(); i++) {
                renderer.setSeriesPaint(i, getRandomColor(i));
                renderer.setSeriesStroke(i, new BasicStroke(2.5f));
                renderer.setSeriesShapesVisible(i, true);
                renderer.setSeriesShape(i, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
            }
            plot.setRenderer(renderer);

            chart.getTitle().setFont(new Font("Arial", Font.BOLD, 40));
            chart.getTitle().setPaint(Color.WHITE);
            plot.getDomainAxis().setLabelFont(loadFontFromFile(true).deriveFont(14f));
            plot.getRangeAxis().setLabelFont(loadFontFromFile(true).deriveFont(14f));

            BufferedImage background = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            BufferedImage chartImage = chart.createBufferedImage(
                    (int) (background.getWidth() * 0.8),
                    (int) (background.getHeight() * 0.8));


            Graphics2D g = background.createGraphics();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Ensures proper blending
            g.drawImage(chartImage, (int) (background.getWidth() * 0.1), (int) (background.getHeight() * 0.1), null);
            g.dispose();


            File chartFile = File.createTempFile("charts", ".png");
            ImageIO.write(background, "png", chartFile);
            return chartFile.getAbsolutePath();
        } catch (Exception e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    private static Color getRandomColor(int index) {
//        Color[] colors = {
//                new Color(134, 0, 125),
//                new Color(134, 0, 125, 160),
//                new Color(134, 0, 125, 90),
//                new Color(134, 0, 125, 40)
//        };

        Color[] colors = {
                Color.blue, Color.green, Color.red, Color.yellow
        };
        return colors[index % colors.length];
    }

    public void weeklyResultNotification() {
        try {
            MultipartBody body = Unirest.post(urlPhoto)
                    .headers(Map.of("accept", "application/json",
                                    "content-type", "application/json"
                            )
                    )
                    .queryString("chat_id", chatId)
                    .field("photo", new File(Objects.requireNonNull(
                            createWeeklyImage()
                    )));
            HttpResponse<String> response = body.asString();
            if (response.getStatus() == 200) {
                serverLogger.info("Message weekly results notification has been send");
            } else {
                serverLogger.warn("Don't send weekly results notification");
            }
        } catch (UnirestException e) {
            String message = "Sending weekly result notification message error";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
        }
    }

    private void sendNotification(Notification notification) throws UnirestException, IOException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        long minutesBeforeMatch = Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes();
        String chatId = notification.getUser().getTelegramId();

        String homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Сделать прогноз")
                .callbackData("/" + homeTeam + ":" + awayTeam + "_")
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(Collections.singleton(List.of(button)))
                .build();

        String matchTime = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());

        MultipartBody body = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .queryString("caption",
                        "Не проставлен прогноз на матч\n" +
                        "Осталось " + minutesBeforeMatch +
                        (minutesBeforeMatch % 10 == 1 ? " минута" :
                                minutesBeforeMatch > 20 && List.of(2L, 3L, 4L).contains(minutesBeforeMatch % 10) ? " минуты" : " минут"
                        )
                )
                .queryString("reply_markup", objectMapper.writeValueAsString(markup))
                .field("photo", new File(Objects.requireNonNull(
                        createImage(
                                match.getPublicId(),
                                match.getHomeTeamId(),
                                match.getAwayTeamId(),
                                matchTime,
                                "notification",
                                null
                        )
                )));

        HttpResponse<String> response = body.asString();
        if (response.getStatus() == 200) {
            serverLogger.info("Not predictable match {}:{} notification has been send to {}",
                    homeTeam,
                    awayTeam,
                    notification.getUser().getLogin()
            );
        } else {
            serverLogger.warn("Don't send not predictable match notification");
        }
    }

    public String createImage(Integer matchPublicId, Integer homeTeamId, Integer awayTeamId, String centerInfo, String method, List<Result> results) throws UnirestException {
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();

            BufferedImage matchBlock = new BufferedImage((int) (WIDTH * 0.90), 200, BufferedImage.TYPE_INT_ARGB);
            int matchBlockHeight = matchBlock.getHeight();
            int matchBlockWidth = matchBlock.getWidth();

            BufferedImage homeTeamPic = scaleImage(ImageIO.read(new ClassPathResource("static/img/teams/" + homeTeamId + ".webp").getInputStream()), matchBlockHeight);
            BufferedImage awayTeamPic = scaleImage(ImageIO.read(new ClassPathResource("static/img/teams/" + awayTeamId + ".webp").getInputStream()), matchBlockHeight);

            Graphics2D blockG2d = matchBlock.createGraphics();
            BufferedImage fill = generateWithGradient(matchBlockWidth - matchBlockHeight, (int) (matchBlockHeight * 0.6), homeTeamId, awayTeamId);
            blockG2d.drawImage(fill, (matchBlockWidth - fill.getWidth()) / 2, (matchBlockHeight - fill.getHeight()) / 2, null);

            blockG2d.setPaint(new Color(255, 255, 255, 50));
            blockG2d.fillRect(matchBlockWidth / 2 - 100, (matchBlockHeight - fill.getHeight()) / 2, 200, fill.getHeight());

            blockG2d.drawImage(homeTeamPic, null, 0, 0);
            blockG2d.drawImage(awayTeamPic, null, matchBlockWidth - matchBlockHeight, 0);

            blockG2d.setColor(Color.WHITE);
            Font font = loadFontFromFile(false).deriveFont(60f);
            blockG2d.setFont(font);

            FontMetrics fontMetrics = blockG2d.getFontMetrics();
            Rectangle2D centerBounds = fontMetrics.getStringBounds(centerInfo, blockG2d);
            int infoWidth = blockG2d.getFontMetrics().stringWidth(centerInfo);
            int infoX = (matchBlockWidth - infoWidth) / 2;
            int infoY = (int) ((double) matchBlockHeight / 2 - centerBounds.getHeight() / 2 - centerBounds.getY());
            blockG2d.drawString(centerInfo, infoX, infoY);

            font = loadFontFromFile(true).deriveFont(80f);
            blockG2d.setFont(font);

            String homeTeamCode = DaoUtil.TEAMS.get(homeTeamId).getCode();
            fontMetrics = blockG2d.getFontMetrics();
            Rectangle2D homeBounds = fontMetrics.getStringBounds(homeTeamCode, blockG2d);
            int text1Width = fontMetrics.stringWidth(homeTeamCode);
            int homeX = matchBlockWidth / 2 - 120 - text1Width;
            int homeY = (int) ((double) matchBlockHeight / 2 - homeBounds.getHeight() / 2 - homeBounds.getY());
            blockG2d.drawString(homeTeamCode, homeX, homeY);

            String awayTeamCode = DaoUtil.TEAMS.get(awayTeamId).getCode();
            Rectangle2D awayBounds = fontMetrics.getStringBounds(awayTeamCode, blockG2d);
            int awayX = matchBlockWidth / 2 + 120;
            int awayY = (int) ((double) matchBlockHeight / 2 - awayBounds.getHeight() / 2 - awayBounds.getY());
            blockG2d.drawString(awayTeamCode, awayX, awayY);

            blockG2d.dispose();

            g2d.drawImage(matchBlock, (WIDTH - matchBlockWidth) / 2, (HEIGHT - matchBlockHeight) / 2, null);

            int middleY = image.getHeight() / 2;

            switch (method) {
                case "notification" -> {
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

                    font = loadFontFromFile(false).deriveFont(30f * ((float) picSize / 40));
                    g2d.setFont(font);
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));

                    String info = "last 6";
                    fontMetrics = g2d.getFontMetrics();
                    centerBounds = fontMetrics.getStringBounds(info, g2d);
                    infoY = (int) ((double) HEIGHT / 2 + 130 - centerBounds.getHeight() / 2 - centerBounds.getY()) + (picSize * 2 + 10) / 2;
                    g2d.drawString(info, (WIDTH - fontMetrics.stringWidth(info)) / 2, infoY);

                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                    font = loadFontFromFile(false).deriveFont(40f);
                    g2d.setFont(font);

                    if (odd != null) {
                        Double homeTeamOdd = odd.home();
                        int text3Width = g2d.getFontMetrics().stringWidth(String.valueOf(homeTeamOdd));
                        int text3X = WIDTH / 4 - (text3Width / 2);
                        int text3Y = middleY + 400;
                        g2d.drawString(String.valueOf(homeTeamOdd), text3X, text3Y);

                        Double drawOdd = odd.draw();
                        int text4Width = g2d.getFontMetrics().stringWidth(String.valueOf(drawOdd));
                        int text4X = (WIDTH / 2) - (text4Width / 2);
                        int text4Y = middleY + 400;
                        g2d.drawString(String.valueOf(drawOdd), text4X, text4Y);

                        Double awayTeamOdd = odd.away();
                        int text5Width = g2d.getFontMetrics().stringWidth(String.valueOf(awayTeamOdd));
                        int text5X = WIDTH * 3 / 4 - (text5Width / 2);
                        int text5Y = middleY + 400;
                        g2d.drawString(String.valueOf(awayTeamOdd), text5X, text5Y);

                        g2d.setColor(new Color(255, 255, 255, 50));
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int rectWidth = 160;
                        int rectHeight = 80;
                        int arcRadius = 40;

                        String win1 = "1";
                        int win1Width = g2d.getFontMetrics().stringWidth(win1);
                        int win1X = WIDTH / 4 - (win1Width / 2);
                        int win1Y = middleY + 320;
                        g2d.drawString(win1, win1X, win1Y);
                        String win2 = "2";
                        int win2Width = g2d.getFontMetrics().stringWidth(win2);
                        int win2X = WIDTH * 3 / 4 - (win2Width / 2);
                        int win2Y = middleY + 320;
                        g2d.drawString(win2, win2X, win2Y);
                        String draw = "X";
                        int drawWidth = g2d.getFontMetrics().stringWidth(draw);
                        int drawX = (WIDTH / 2) - (drawWidth / 2);
                        int drawY = middleY + 320;
                        g2d.drawString(draw, drawX, drawY);

                        g2d.fillRoundRect(WIDTH / 4 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH / 2 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH * 3 / 4 - rectWidth / 2, middleY + 348, rectWidth, rectHeight, arcRadius, arcRadius);
                    }
                }
                case "yourPredict" -> {
                    font = new Font("Arial", Font.BOLD, 40);
                    g2d.setFont(font);
                    String message = "ТВОЙ ПРОГНОЗ";
                    int text4Width = g2d.getFontMetrics().stringWidth(message);
                    int text4X = (WIDTH / 2) - (text4Width / 2);
                    int text4Y = middleY + 280;
                    g2d.drawString(message, text4X, text4Y);
                }
                case "result" -> {
                    BufferedImage resultImage = new BufferedImage(WIDTH / 2, HEIGHT / 6, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D resG2d = resultImage.createGraphics();

                    int backgroundWidth = resultImage.getWidth();
                    int backgroundHeight = resultImage.getHeight();

                    int midY = backgroundHeight / 2;
                    int midX = backgroundWidth / 2;

                    int offsetY = (int) (backgroundHeight * 0.1);
                    int offsetX = (int) (backgroundWidth * 0.05);

                    int backgroundX = 0;
                    int backgroundY = 0;

                    resG2d.setColor(new Color(255, 255, 255, 30));
                    int arcWidth = 20;
                    int arcHeight = 20;
                    resG2d.fillRoundRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight, arcWidth, arcHeight);

                    resG2d.setColor(Color.WHITE);
                    font = loadFontFromFile(false).deriveFont(32f);
                    resG2d.setFont(font);

                    for (int i = 0; i < results.size(); i++) {
                        String result = results.get(i).login + " " + results.get(i).predict + " [" + results.get(i).point + "]";
                        int resultWidth = resG2d.getFontMetrics().stringWidth(result);

                        if (i == 0) {
                            int resultX = midX - resultWidth - offsetX;
                            int resultY = midY - offsetY * 2;
                            resG2d.drawString(result, resultX, resultY);
                        } else if (i == 1) {
                            int resultX = midX + offsetX;
                            int resultY = midY - offsetY * 2;
                            resG2d.drawString(result, resultX, resultY);
                        } else if (i == 2) {
                            int resultX = midX - resultWidth - offsetX;
                            int resultY = midY + offsetY * 3;
                            resG2d.drawString(result, resultX, resultY);
                        } else if (i == 3) {
                            int resultX = midX + offsetX;
                            int resultY = midY + offsetY * 3;
                            resG2d.drawString(result, resultX, resultY);
                        }
                    }
                    resG2d.dispose();
                    g2d.drawImage(resultImage, (WIDTH - resultImage.getWidth()) / 2, middleY + 150, null);
                }
            }

            g2d.dispose();

            File tempFile = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", tempFile);

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    private String createWeeklyImage() {
        int id = DaoUtil.currentWeekId;
        Map<String, Integer> usersPoints = predictionService.getWeeklyUsersPoints(id);
        int y = (HEIGHT - usersPoints.size() * 60) / 2;
        AtomicInteger stBlockY = new AtomicInteger(y);
        try {
            BufferedImage image = generateWithBackground(WIDTH, HEIGHT, BACKGROUND_COLOR);
            Graphics2D g2d = image.createGraphics();

            int midX = WIDTH / 2;

            AtomicInteger place = new AtomicInteger(1);

            usersPoints.forEach((login, pts) -> {
                BufferedImage stBlock = new BufferedImage((int) (WIDTH * 0.7), 90, BufferedImage.TYPE_INT_ARGB);
                Graphics2D stGraphics = stBlock.createGraphics();

                Color color = new Color(255, 255, 255, 80);
                Color placeColor;
                switch (place.get()) {
                    case 1 -> placeColor = new Color(134, 0, 125, 240);
                    case 2 -> placeColor = new Color(134, 0, 125, 160);
                    case 3 -> placeColor = new Color(134, 0, 125, 80);
                    default -> placeColor = new Color(255, 255, 255, 0);
                }

                stGraphics.setPaint(placeColor);
                stGraphics.fillRect(0, 0, (int) (stBlock.getWidth() * 0.15), 90);
                stGraphics.setPaint(color);
                stGraphics.fillRect((int) (stBlock.getWidth() * 0.15), 0, stBlock.getWidth() - (int) (stBlock.getWidth() * 0.15), 90);
                stGraphics.setPaint(Color.WHITE);

                Font font = loadFontFromFile(true).deriveFont(70f);
                stGraphics.setFont(font);

                FontMetrics fontMetrics = stGraphics.getFontMetrics();

                String plc = String.valueOf(place.getAndIncrement());
                Rectangle2D plcBounds = fontMetrics.getStringBounds(plc, stGraphics);
                int plcWidth = fontMetrics.stringWidth(plc);
                int plcX = ((int) (stBlock.getWidth() * 0.15) - plcWidth) / 2;
                int plcY = (int) ((double) stBlock.getHeight() / 2 - plcBounds.getHeight() / 2 - plcBounds.getY());

                stGraphics.drawString(plc, plcX, plcY);

                String lgn = login.toUpperCase().substring(0, 3);
                Rectangle2D loginBounds = fontMetrics.getStringBounds(lgn, stGraphics);
                int lgnX = (int) (stBlock.getWidth() * 0.25);
                int lgnY = (int) ((double) stBlock.getHeight() / 2 - loginBounds.getHeight() / 2 - loginBounds.getY());

                stGraphics.drawString(lgn, lgnX, lgnY);

                Rectangle2D ptsBounds = fontMetrics.getStringBounds(String.valueOf(pts), stGraphics);
                int ptsX = (int) (stBlock.getWidth() * 0.95) - fontMetrics.stringWidth(String.valueOf(pts));
                int ptsY = (int) ((double) stBlock.getHeight() / 2 - ptsBounds.getHeight() / 2 - ptsBounds.getY());

                stGraphics.drawString(String.valueOf(pts), ptsX, ptsY);

                stGraphics.dispose();
                g2d.drawImage(stBlock, midX - stBlock.getWidth() / 2, stBlockY.getAndAdd(110), null);
            });

            g2d.setPaint(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60));

            FontMetrics fontMetrics = g2d.getFontMetrics();

            String title = "РЕЗУЛЬТАТЫ " + id + " ТУРА";
            Rectangle2D ttlBounds = fontMetrics.getStringBounds(title, g2d);
            int ttlWidth = fontMetrics.stringWidth(title);
            int ttlX = (WIDTH - ttlWidth) / 2;
            int ttlY = (int) (y - 40 - ttlBounds.getHeight());

            g2d.drawString(title, ttlX, ttlY);

            g2d.dispose();

            File tempFile = File.createTempFile("weekly", ".png");
            ImageIO.write(image, "png", tempFile);

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            String message = "Error creating image";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
            return null;
        }
    }

    private void drawHeadToHead(Graphics2D g2d, List<HeadToHead> h2h, Integer homeTeamId, Integer awayTeamId, int picSize) throws IOException {
        Map<Integer, BufferedImage> pics = Map.of(
                homeTeamId, grayScaling(
                        scaleImage(
                                ImageIO.read(new ClassPathResource("static/img/teams/" + homeTeamId + ".webp").getInputStream()), picSize
                        )
                ),
                awayTeamId, grayScaling(
                        scaleImage(
                                ImageIO.read(new ClassPathResource("static/img/teams/" + awayTeamId + ".webp").getInputStream()), picSize
                        )
                )
        );

        int h2hNum = h2h.size();
        BufferedImage h2hBlock = new BufferedImage((picSize * 3 + 10) * 4, (picSize + 10) * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D h2hBlockGraphics = h2hBlock.createGraphics();
        int x = 0;
        if (h2hNum <= 6) {
            x = picSize * 3 / 2 + 10;
        }
        int y = picSize + 10;
        int h2hCount = 1;
        for (HeadToHead headToHead : h2h) {
            BufferedImage matchBlock = new BufferedImage(picSize * 3, picSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D matchG2d = matchBlock.createGraphics();
            Font font = loadFontFromFile(false).deriveFont(28f * ((float) picSize / 40));
            matchG2d.setFont(font);
            matchG2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));

            matchG2d.drawImage(pics.get(headToHead.getHomeTeamId()), 0, 0, null);
            matchG2d.drawImage(pics.get(headToHead.getAwayTeamId()),
                    matchBlock.getWidth() - pics.get(headToHead.getAwayTeamId()).getWidth(), 0, null);

            String result = headToHead.getHomeTeamScore() + ":" + headToHead.getAwayTeamScore();

            FontMetrics fontMetrics = matchG2d.getFontMetrics();
            Rectangle2D centerBounds = fontMetrics.getStringBounds(result, matchG2d);
            int resultWidth = fontMetrics.stringWidth(result);
            int resultX = (matchBlock.getWidth() - resultWidth) / 2;
            int resultY = (int) ((double) matchBlock.getHeight() / 2 - centerBounds.getHeight() / 2 - centerBounds.getY());
            matchG2d.drawString(result, resultX, resultY);
            matchG2d.dispose();

            h2hBlockGraphics.drawImage(matchBlock, x, y, null);
            if (h2hNum <= 6 && h2hCount == 3 || h2hNum > 6 && h2hCount == 4) {
                x = (h2hBlock.getWidth() - (picSize * 3 + 10) * (h2hNum - h2hCount) + 10) / 2;
                y += picSize + 10;
            } else {
                x += matchBlock.getWidth() + 10;
            }

            h2hCount++;
        }
        String type = "head to head";
        Font font = loadFontFromFile(false).deriveFont(30f * ((float) picSize / 40));
        h2hBlockGraphics.setFont(font);
        h2hBlockGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        FontMetrics fontMetrics = h2hBlockGraphics.getFontMetrics();
        int typeWidth = fontMetrics.stringWidth(type);
        int typeX = (h2hBlock.getWidth() - typeWidth) / 2;
        h2hBlockGraphics.drawString(type, typeX, picSize - 10);

        h2hBlockGraphics.dispose();

        g2d.drawImage(h2hBlock, (WIDTH - h2hBlock.getWidth()) / 2, HEIGHT / 2 - 280 - (picSize - 40) * 3, null);
    }

    private void drawLastMatchesInfo(Graphics2D g2d, List<Match> matches, int teamId, int picSize, boolean isHome) throws IOException {
        int x = 0;
        int y = 0;
        int matchNum = 1;
        BufferedImage matchesBlock = new BufferedImage((picSize * 2 + 10) * 3, picSize * 2 + 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mBg2d = matchesBlock.createGraphics();
        for (Match match : matches) {
            int againstTeamCode = match.getHomeTeamId() == teamId ? match.getAwayTeamId() : match.getHomeTeamId();

            BufferedImage againstTeamPic = grayScaling(
                    scaleImage(
                            ImageIO.read(new ClassPathResource("static/img/teams/" + againstTeamCode + ".webp").getInputStream()), picSize
                    )
            );
            BufferedImage matchBlock = new BufferedImage(picSize * 2, picSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D mG2d = matchBlock.createGraphics();
            Font font = loadFontFromFile(false).deriveFont(28f * ((float) picSize / 40));
            mG2d.setFont(font);
            mG2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));

            String result = match.getHomeTeamId() == teamId ?
                    match.getHomeTeamScore() + ":" + match.getAwayTeamScore() :
                    match.getAwayTeamScore() + ":" + match.getHomeTeamScore();

            FontMetrics fontMetrics = mG2d.getFontMetrics();
            Rectangle2D centerBounds = fontMetrics.getStringBounds(result, mG2d);
            int infoY = (int) ((double) matchBlock.getHeight() / 2 - centerBounds.getHeight() / 2 - centerBounds.getY());
            mG2d.drawString(result, picSize, infoY);
            mG2d.drawImage(againstTeamPic, 0, 0, null);
            mG2d.dispose();

            mBg2d.drawImage(matchBlock, x, y, null);
            if (matchNum == 3) {
                x = 0;
                y += matchBlock.getHeight() + 10;
            } else {
                x += matchBlock.getWidth() + 10;
            }
            matchNum++;
        }
        mBg2d.dispose();
        if (isHome) {
            g2d.drawImage(matchesBlock, WIDTH / 2 - 60 - matchesBlock.getWidth(), HEIGHT / 2 + 130, null);
        } else {
            g2d.drawImage(matchesBlock, WIDTH / 2 + 60, HEIGHT / 2 + 130, null);
        }
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
            p = (a << 24) | (avg << 16) | (avg << 8) | avg;
            pixels[i] = p;
        }
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    private BufferedImage generateWithGradient(int width, int height, int homeTeamId, int awayTeamId) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Color homeColors = teamColors.get(String.valueOf(homeTeamId)).home();
        Color awayColors = teamColors.get(String.valueOf(awayTeamId)).away();

        if (similarTo(homeColors, awayColors)) {
            awayColors = teamColors.get(String.valueOf(awayTeamId)).third();
        }

        Graphics2D g2d = image.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, homeColors, width, 0, awayColors);
        g2d.setPaint(gradient);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2d.fillRect(0, 0, width, height);

        g2d.dispose();
        return image;
    }

    private BufferedImage generateWithBackground(int width, int height, Color color) throws IOException {
        int stripeWidth = 20;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, color, width, 0, color);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, width);

        BufferedImage stripe = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
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

        BufferedImage logo = scaleImage(ImageIO.read(new ClassPathResource("static/img/pl_logo.webp").getInputStream()), 1600);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.04f));
        g2d.drawImage(logo, -400, -150, null);

        g2d.dispose();
        return image;
    }

    @PostConstruct
    public void initTeamColors() {
        try (InputStream teamColorsInput = getClass().getClassLoader().getResourceAsStream("team_colors.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            if (teamColorsInput != null) {
                JsonNode jsonNode = objectMapper.readTree(teamColorsInput);
                jsonNode.fields().forEachRemaining(teamNode -> {
                    String teamId = teamNode.getKey();
                    JsonNode teamColorNode = teamNode.getValue();
                    JsonNode homeColorNode = teamColorNode.get("home");
                    JsonNode awayColorNode = teamColorNode.get("away");
                    JsonNode thirdColorNode = teamColorNode.get("third");
                    teamColors.put(
                            teamId,
                            new TeamColor(
                                    new Color(homeColorNode.get("r").asInt(), homeColorNode.get("g").asInt(), homeColorNode.get("b").asInt()),
                                    new Color(awayColorNode.get("r").asInt(), awayColorNode.get("g").asInt(), awayColorNode.get("b").asInt()),
                                    new Color(thirdColorNode.get("r").asInt(), thirdColorNode.get("g").asInt(), thirdColorNode.get("b").asInt())
                            )
                    );
                });
            }
        } catch (Exception e) {
            String message = "Error creating team colors";
            panicSender.sendPanic(message, e);
            serverLogger.error("{}: {}", message, e.getMessage());
        }
    }

    private Font loadFontFromFile(boolean condensed) {
        try {
            String fontName = "pl-" + (condensed ? "cond" : "") + "bold.ttf";
            return Font.createFont(Font.TRUETYPE_FONT, new ClassPathResource("static/" + fontName).getInputStream());
        } catch (Exception e) {
            serverLogger.error("Error loading font: {}", e.getMessage());
            return new Font("Arial", Font.BOLD, 30);
        }
    }

    private record TeamColor(Color home, Color away, Color third) {
    }

    private record Result(String login, String predict, int point) {
    }

    private record MatchRecord(int homeTeamId, int awayTeamId, int weekId, LocalDateTime localDateTime) {
    }
}
