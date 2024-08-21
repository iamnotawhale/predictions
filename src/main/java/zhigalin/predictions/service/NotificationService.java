package zhigalin.predictions.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.MultipartBody;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.DaoUtil;

@Log4j2
@Service
public class NotificationService {
    private final PredictionService predictionService;
    @Value("${bot.urlMessage}")
    private String urlMessage;
    @Value("${bot.urlPhoto}")
    private String urlPhoto;

    private final UserService userService;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;
    private final Map<Integer, List<String>> notificationBLackList = new HashMap<>();
    private final Map<String, TeamColor> teamColors = new HashMap<>();

    public NotificationService(UserService userService, MatchService matchService, PredictionService predictionService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.matchService = matchService;
        this.objectMapper = objectMapper;
        notificationBLackList.put(30, new ArrayList<>());
        notificationBLackList.put(90, new ArrayList<>());
        this.predictionService = predictionService;
    }

    public void check() throws UnirestException, IOException {
        for (Integer minutes : List.of(90, 30)) {
            List<User> users = userService.findAll();
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
                                sendPhoto(notification);
                            }
                        }
                    }
                }
            } else {
                notificationBLackList.get(minutes).clear();
            }
        }
    }

    private void sendPhoto(Notification notification) throws UnirestException, IOException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        String chatId = notification.getUser().getTelegramId();

        String homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Сделать прогноз")
                .callbackData("/" + homeTeam + ":" + awayTeam)
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(Collections.singleton(List.of(button)))
                .build();

        Unirest.get(urlMessage)
                .queryString("chat_id", chatId)
                .queryString("text", "Не проставлен прогноз на матч")
                .queryString("parse_mode", "Markdown")
                .asString();

        MultipartBody body = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .queryString("text", "Не проставлен прогноз на матч")
                .queryString("reply_markup", objectMapper.writeValueAsString(markup))
                .field("photo", new File(Objects.requireNonNull(createImage(match))));

        HttpResponse<String> response = body.asString();
        if (response.getStatus() == 200) {
            log.info("Not predictable match {}:{} notification has been send to {}",
                    homeTeam,
                    awayTeam,
                    notification.getUser().getLogin()
            );
        } else {
            log.warn("Don't send not predictable match notification");
        }
    }

    private String createImage(Match match) {
        try {
            BufferedImage image = generateWithGradient(match.getHomeTeamId(), match.getAwayTeamId());
            Graphics2D g2d = image.createGraphics();

            BufferedImage homeTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getHomeTeamId() + ".webp").getFile());
            BufferedImage awayTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getAwayTeamId() + ".webp").getFile());

            int middleY = (image.getHeight() - 100) / 2;
            g2d.drawImage(homeTeamPic, 512 / 4 - 50, middleY, 100, 100, null);
            g2d.drawImage(awayTeamPic, 512 * 3 / 4 - 50, middleY, 100, 100, null);

            String text = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());
            g2d.setColor(Color.WHITE);
            Font font = loadFontFromFile().deriveFont(30f);
            g2d.setFont(font);
            int textWidth = g2d.getFontMetrics().stringWidth(text);
            int textX = (512 / 2) - (textWidth / 2);
            g2d.drawString(text, textX, middleY + 70);

            g2d.setColor(Color.WHITE);
            g2d.setFont(font);

            String homeTeamCode = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
            int text1Width = g2d.getFontMetrics().stringWidth(homeTeamCode);
            int text1X = 512 / 4 - (text1Width / 2);
            int text1Y = middleY + 100 + 30;
            g2d.drawString(homeTeamCode, text1X, text1Y);

            String awayTeamCode = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
            int text2Width = g2d.getFontMetrics().stringWidth(awayTeamCode);
            int text2X = 512 * 3 / 4 - (text2Width / 2);
            int text2Y = middleY + 100 + 30;
            g2d.drawString(awayTeamCode, text2X, text2Y);

            g2d.dispose();

            File tempFile = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", tempFile);

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            log.error("Error creating image: {}", e.getMessage());
            return null;
        }

    }

    private BufferedImage generateWithGradient(int homeTeamId, int awayTeamId) {
        BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);

        TeamColor homeColors = teamColors.get(String.valueOf(homeTeamId));
        TeamColor awayColors = teamColors.get(String.valueOf(awayTeamId));

        Color homeColor = new Color(homeColors.red, homeColors.green, homeColors.blue);
        Color awayColor = new Color(awayColors.red, awayColors.green, awayColors.blue);

        int startRed = homeColor.getRed();
        int startGreen = homeColor.getGreen();
        int startBlue = homeColor.getBlue();

        int endRed = awayColor.getRed();
        int endGreen = awayColor.getGreen();
        int endBlue = awayColor.getBlue();

        int redDifference = endRed - startRed;
        int greenDifference = endGreen - startGreen;
        int blueDifference = endBlue - startBlue;

        Graphics2D g2d = image.createGraphics();
        for (int x = 0; x < 512; x++) {
            int red = startRed + (int) (((double) x / 512) * redDifference);
            int green = startGreen + (int) (((double) x / 512) * greenDifference);
            int blue = startBlue + (int) (((double) x / 512) * blueDifference);
            g2d.setColor(new Color(red, green, blue));
            g2d.fillRect(x, 0, 1, 256);
        }

        g2d.dispose();
        return image;
    }

    @PostConstruct
    public void initTeamColors() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("team_colors.json")) {
            if (input != null) {
                JsonNode jsonNode = new ObjectMapper().readTree(input);
                jsonNode.fields().forEachRemaining(teamNode -> {
                    String teamId = teamNode.getKey();
                    JsonNode teamColorNode = teamNode.getValue();
                    teamColors.put(
                            teamId,
                            new TeamColor(
                                    teamColorNode.get("r").asInt(),
                                    teamColorNode.get("g").asInt(),
                                    teamColorNode.get("b").asInt()
                            )
                    );
                });
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private record TeamColor(int red, int green, int blue) {
    }

    private Font loadFontFromFile() {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ClassPathResource("static/pl-bold.ttf").getFile());
        } catch (Exception e) {
            System.err.println("Error loading font: " + e.getMessage());
            return new Font("Arial", Font.BOLD, 30);
        }
    }
}
