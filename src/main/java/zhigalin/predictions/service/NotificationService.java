package zhigalin.predictions.service;

import java.awt.*;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.MultipartBody;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
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
    private final UserService userService;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;
    private final Map<String, TeamColor> teamColors = new HashMap<>();
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    private final Map<Integer, List<String>> notificationBLackList = new HashMap<>();
    private final Set<Integer> notificationBan = new HashSet<>();

    private static final int WIDTH = 1024;
    private static final int HEIGHT = 1024;
    private static final Color BACKGROUND_COLOR = new Color(44, 0, 48);

    public NotificationService(
            UserService userService, MatchService matchService, PredictionService predictionService,
            ObjectMapper objectMapper, PanicSender panicSender, OddsService oddsService
    ) {
        this.userService = userService;
        this.matchService = matchService;
        this.objectMapper = objectMapper;
        notificationBLackList.put(30, new ArrayList<>());
        notificationBLackList.put(90, new ArrayList<>());
        this.predictionService = predictionService;
        this.panicSender = panicSender;
        this.oddsService = oddsService;
    }

    //            @Scheduled(initialDelay = 1000, fixedDelay = 60000000)
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

    public void check() throws UnirestException, IOException {
        for (Integer minutes : List.of(90, 30)) {
            List<User> users = userService.findAll().stream().filter(user -> !user.getTelegramId().isEmpty()).toList();
            List<Match> nearest = matchService.findAllNearest(minutes);
            if (!nearest.isEmpty()) {
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

    public void fullTimeMatchNotification() {
        List<Match> online = matchService.findOnlineMatches();
        if (!online.isEmpty()) {
            for (Match match : online) {
                String centerInfo;
                Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
                Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
                predictionService.updateByMatch(match);
                if (match.getStatus().equals("ft") && !notificationBan.contains(match.getPublicId())) {
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

                    notificationBan.add(match.getPublicId());
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
            }
        } else {
            if (!notificationBan.isEmpty()) {
                notificationBan.clear();
            }
        }
    }

    public void weeklyResultNotification() {
        int id = DaoUtil.currentWeekId;
        Map<String, Integer> currentWeekUsersPoints = predictionService.getWeeklyUsersPoints(id);
        StringBuilder builder = new StringBuilder();
        builder.append("Очки за тур: ").append("\n");
        for (Map.Entry<String, Integer> entry : currentWeekUsersPoints.entrySet()) {
            builder.append(entry.getKey().toUpperCase(), 0, 3).append(" ")
                    .append(entry.getValue()).append(" pts").append("\n");
        }
        try {
            HttpResponse<String> response = Unirest.get(urlMessage)
                    .queryString("chat_id", chatId)
                    .queryString("text", builder.toString())
                    .queryString("parse_mode", "Markdown")
                    .asString();
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
                    font = loadFontFromFile(false).deriveFont(40f);
                    g2d.setFont(font);
                    Odd odd = ODDS.getOrDefault(matchPublicId, null);
                    if (odd != null) {
                        Double homeTeamOdd = odd.home();
                        int text3Width = g2d.getFontMetrics().stringWidth(String.valueOf(homeTeamOdd));
                        int text3X = WIDTH / 4 - (text3Width / 2);
                        int text3Y = middleY + 280;
                        g2d.drawString(String.valueOf(homeTeamOdd), text3X, text3Y);

                        Double drawOdd = odd.draw();
                        int text4Width = g2d.getFontMetrics().stringWidth(String.valueOf(drawOdd));
                        int text4X = (WIDTH / 2) - (text4Width / 2);
                        int text4Y = middleY + 280;
                        g2d.drawString(String.valueOf(drawOdd), text4X, text4Y);

                        Double awayTeamOdd = odd.away();
                        int text5Width = g2d.getFontMetrics().stringWidth(String.valueOf(awayTeamOdd));
                        int text5X = WIDTH * 3 / 4 - (text5Width / 2);
                        int text5Y = middleY + 280;
                        g2d.drawString(String.valueOf(awayTeamOdd), text5X, text5Y);

                        g2d.setColor(new Color(255, 255, 255, 50));
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int rectWidth = 160;
                        int rectHeight = 80;
                        int arcRadius = 40;

                        String win1 = "1";
                        int win1Width = g2d.getFontMetrics().stringWidth(win1);
                        int win1X = WIDTH / 4 - (win1Width / 2);
                        int win1Y = middleY + 200;
                        g2d.drawString(win1, win1X, win1Y);
                        String win2 = "2";
                        int win2Width = g2d.getFontMetrics().stringWidth(win2);
                        int win2X = WIDTH * 3 / 4 - (win2Width / 2);
                        int win2Y = middleY + 200;
                        g2d.drawString(win2, win2X, win2Y);
                        String draw = "X";
                        int drawWidth = g2d.getFontMetrics().stringWidth(draw);
                        int drawX = (WIDTH / 2) - (drawWidth / 2);
                        int drawY = middleY + 200;
                        g2d.drawString(draw, drawX, drawY);

                        g2d.fillRoundRect(WIDTH / 4 - rectWidth / 2, middleY + 228, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH / 2 - rectWidth / 2, middleY + 228, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH * 3 / 4 - rectWidth / 2, middleY + 228, rectWidth, rectHeight, arcRadius, arcRadius);
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

    private record TeamColor(Color home, Color away, Color third) {
    }

    private record Result(String login, String predict, int point) {
    }

    private record MatchRecord(int homeTeamId, int awayTeamId, int weekId, LocalDateTime localDateTime) {
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
}
