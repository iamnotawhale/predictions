package zhigalin.predictions.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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

    private static final Map<Integer, Map<Integer, Map<String, Double>>> CORDS = new LinkedHashMap<>();

    private static final int WIDTH = 1024;
    private static final int HEIGHT = 512;

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
                serverLogger.warn("Don't send today's match notification");
            }
        }
    }

    private String createTodayMatchesImage(List<MatchRecord> list) {
        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            Map<Integer, Map<String, Double>> map = CORDS.get(list.size());
            for (Map.Entry<Integer, Map<String, Double>> entry : map.entrySet()) {
                Integer matchNum = entry.getKey();
                Map<String, Double> cordInfo = entry.getValue();

                MatchRecord matchRecord = list.get(matchNum - 1);

                double width = (double) cordInfo.get("width");
                double height = (double) cordInfo.get("height");

                BufferedImage block = generateWithGradient(
                        (int) (WIDTH * width),
                        (int) (HEIGHT * height),
                        matchRecord.homeTeamId,
                        matchRecord.awayTeamId
                );

                int blockHeight = block.getHeight();
                int blockWidth = block.getWidth();

                int scale = (int) ((1 / width) * (1 / height));

                int blockScale = switch (scale) {
                    case 16 -> 1;
                    case 9, 8, 6, 5 -> 2;
                    case 4, 3 -> 3;
                    case 2 -> 4;
                    case 1 -> 5;
                    default -> throw new IllegalStateException("Unexpected value: " + scale);
                };

                BufferedImage homeTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + matchRecord.homeTeamId + ".webp").getInputStream());
                BufferedImage awayTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + matchRecord.awayTeamId + ".webp").getInputStream());

                Graphics2D blockG2d = block.createGraphics();

                int blockMiddleY = (blockHeight - blockScale * 34) / 2;
                blockG2d.drawImage(homeTeamPic, blockWidth / 4 - blockScale * 20, blockMiddleY, blockScale * 40, blockScale * 40, null);
                blockG2d.drawImage(awayTeamPic, blockWidth * 3 / 4 - blockScale * 20, blockMiddleY, blockScale * 40, blockScale * 40, null);

                blockG2d.setColor(Color.WHITE);
                Font font = loadFontFromFile(blockScale / 2).deriveFont(blockScale * 8f);
                blockG2d.setFont(font);

                int textWidth = blockG2d.getFontMetrics().stringWidth("TOUR " + matchRecord.weekId);
                int textX = (blockWidth / 2) - (textWidth / 2);
                blockG2d.drawString("TOUR " + matchRecord.weekId, textX, blockMiddleY + blockScale * 12);

                font = loadFontFromFile(blockScale / 2).deriveFont(blockScale * 15f);
                blockG2d.setFont(font);

                String time = DateTimeFormatter.ofPattern("HH:mm").format(matchRecord.localDateTime);
                textWidth = blockG2d.getFontMetrics().stringWidth(time);
                textX = (blockWidth / 2) - (textWidth / 2);
                blockG2d.drawString(time, textX, blockMiddleY + blockScale * 25);
                blockG2d.dispose();

                g2d.drawImage(block, null, (int) (WIDTH * (double) cordInfo.get("x")), (int) (HEIGHT * (double) cordInfo.get("y")));
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
            int scale = WIDTH / 512;
            BufferedImage image = generateWithGradient(WIDTH, HEIGHT, homeTeamId, awayTeamId);
            Graphics2D g2d = image.createGraphics();

            BufferedImage homeTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + homeTeamId + ".webp").getInputStream());
            BufferedImage awayTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + awayTeamId + ".webp").getInputStream());

            int middleY = (image.getHeight() - scale * 100) / 2;
            g2d.drawImage(homeTeamPic, WIDTH / 4 - scale * 50, middleY, scale * 100, scale * 100, null);
            g2d.drawImage(awayTeamPic, WIDTH * 3 / 4 - scale * 50, middleY, scale * 100, scale * 100, null);

            g2d.setColor(Color.WHITE);
            Font font = loadFontFromFile(scale).deriveFont(scale * 30f);
            g2d.setFont(font);
            int textWidth = g2d.getFontMetrics().stringWidth(centerInfo);
            int textX = (WIDTH / 2) - (textWidth / 2);
            g2d.drawString(centerInfo, textX, middleY + scale * 50);

            String homeTeamCode = DaoUtil.TEAMS.get(homeTeamId).getCode();
            int text1Width = g2d.getFontMetrics().stringWidth(homeTeamCode);
            int text1X = WIDTH / 4 - (text1Width / 2);
            int text1Y = middleY + scale * -10;
            g2d.drawString(homeTeamCode, text1X, text1Y);

            String awayTeamCode = DaoUtil.TEAMS.get(awayTeamId).getCode();
            int text2Width = g2d.getFontMetrics().stringWidth(awayTeamCode);
            int text2X = WIDTH * 3 / 4 - (text2Width / 2);
            int text2Y = middleY + scale * -10;
            g2d.drawString(awayTeamCode, text2X, text2Y);

            switch (method) {
                case "notification" -> {
                    font = loadFontFromFile(scale).deriveFont(scale * 20f);
                    g2d.setFont(font);
                    Odd odd = ODDS.getOrDefault(matchPublicId, null);
                    if (odd != null) {
                        Double homeTeamOdd = odd.home();
                        int text3Width = g2d.getFontMetrics().stringWidth(String.valueOf(homeTeamOdd));
                        int text3X = WIDTH / 4 - (text3Width / 2);
                        int text3Y = middleY + scale * 140;
                        g2d.drawString(String.valueOf(homeTeamOdd), text3X, text3Y);

                        Double drawOdd = odd.draw();
                        int text4Width = g2d.getFontMetrics().stringWidth(String.valueOf(drawOdd));
                        int text4X = (WIDTH / 2) - (text4Width / 2);
                        int text4Y = middleY + scale * 140;
                        g2d.drawString(String.valueOf(drawOdd), text4X, text4Y);

                        Double awayTeamOdd = odd.away();
                        int text5Width = g2d.getFontMetrics().stringWidth(String.valueOf(awayTeamOdd));
                        int text5X = WIDTH * 3 / 4 - (text5Width / 2);
                        int text5Y = middleY + scale * 140;
                        g2d.drawString(String.valueOf(awayTeamOdd), text5X, text5Y);

                        g2d.setColor(new Color(255, 255, 255, 50));
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int rectWidth = scale * 80;
                        int rectHeight = scale * 40;
                        int arcRadius = scale * 20;

                        g2d.fillRoundRect(WIDTH / 4 - rectWidth / 2, middleY + scale * 114, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH / 2 - rectWidth / 2, middleY + scale * 114, rectWidth, rectHeight, arcRadius, arcRadius);
                        g2d.fillRoundRect(WIDTH * 3 / 4 - rectWidth / 2, middleY + scale * 114, rectWidth, rectHeight, arcRadius, arcRadius);
                    }
                }
                case "yourPredict" -> {
                    font = new Font("Arial", Font.BOLD, scale * 20);
                    g2d.setFont(font);
                    String message = "ТВОЙ ПРОГНОЗ";
                    int text4Width = g2d.getFontMetrics().stringWidth(message);
                    int text4X = (WIDTH / 2) - (text4Width / 2);
                    int text4Y = middleY + scale * 140;
                    g2d.drawString(message, text4X, text4Y);
                }
                case "result" -> {
                    font = loadFontFromFile(scale).deriveFont(scale * 16f);
                    g2d.setFont(font);

                    int textY = middleY + scale * 140;
                    int offsetX = (WIDTH / 2) - (scale * 112);
                    int offsetY = scale * 22;
                    int spacing = scale * 8;

                    for (int i = 0; i < results.size(); i++) {
                        String result = results.get(i).login + " " + results.get(i).predict + " [" + results.get(i).point + "]";
                        String[] parts = result.split(" ");

                        int x = offsetX + (i % 2) * (WIDTH / 4);
                        for (String part : parts) {
                            g2d.drawString(part, x, textY + (i / 2) * offsetY);
                            x += g2d.getFontMetrics().stringWidth(part) + spacing;
                        }
                    }

                    int backgroundWidth = WIDTH / 2;
                    int backgroundHeight = (image.getHeight() / 4) + (scale * 10);
                    int backgroundX = (WIDTH / 2) - backgroundWidth / 2;
                    int backgroundY = image.getHeight() - backgroundHeight + scale * 10;

                    g2d.setColor(new Color(255, 255, 255, 30));
                    int arcWidth = scale * 10;
                    int arcHeight = scale * 10;
                    g2d.fillRoundRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight, arcWidth, arcHeight);
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
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Color homeColors = teamColors.get(String.valueOf(homeTeamId)).home();
        Color awayColors = teamColors.get(String.valueOf(awayTeamId)).away();

        if (similarTo(homeColors, awayColors)) {
            awayColors = teamColors.get(String.valueOf(awayTeamId)).third();
        }

        Graphics2D g2d = image.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, homeColors, WIDTH, 0, awayColors);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        g2d.dispose();
        return image;
    }

    @PostConstruct
    public void initTeamColors() {
        try (InputStream teamColorsInput = getClass().getClassLoader().getResourceAsStream("team_colors.json");
             InputStream matchesLocationInput = getClass().getClassLoader().getResourceAsStream("matches_location.json")) {
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
            if (matchesLocationInput != null) {
                JsonNode jsonNode = objectMapper.readTree(matchesLocationInput);
                jsonNode.fields().forEachRemaining(numberNode -> {
                    int numberOfMatches = Integer.parseInt(numberNode.getKey());
                    Map<Integer, Map<String, Double>> matches = new HashMap<>();
                    JsonNode matchesNode = numberNode.getValue();
                    matchesNode.fields().forEachRemaining(match -> {

                        int matchNum = Integer.parseInt(match.getKey());
                        JsonNode cordsInfo = match.getValue();
                        double x = cordsInfo.get("x").asDouble();
                        double y = cordsInfo.get("y").asDouble();
                        double height = cordsInfo.get("height").asDouble();
                        double width = cordsInfo.get("width").asDouble();

                        Map<String, Double> cords = new HashMap<>();
                        cords.put("x", x);
                        cords.put("y", y);
                        cords.put("height", height);
                        cords.put("width", width);
                        matches.put(matchNum, cords);
                    });
                    CORDS.put(numberOfMatches, matches);
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

    private Font loadFontFromFile(int scale) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ClassPathResource("static/pl-bold.ttf").getInputStream());
        } catch (Exception e) {
            serverLogger.error("Error loading font: {}", e.getMessage());
            return new Font("Arial", Font.BOLD, scale * 30);
        }
    }
}
