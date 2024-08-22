package zhigalin.predictions.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
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

    private static final int WIDTH = 1024;
    private static final int HEIGHT = 512;

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

    private void sendNotification(Notification notification) throws UnirestException, IOException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        long minutesBeforeMatch = Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes();
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
                                minutesBeforeMatch > 20 && List.of(2, 3, 4).contains(minutesBeforeMatch % 10) ? " минуты" : " минут"
                        )
                )
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
            int scale = WIDTH / 512;
            BufferedImage image = generateWithGradient(match.getHomeTeamId(), match.getAwayTeamId());
            Graphics2D g2d = image.createGraphics();

            BufferedImage homeTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getHomeTeamId() + ".webp").getFile());
            BufferedImage awayTeamPic = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getAwayTeamId() + ".webp").getFile());

            int middleY = (image.getHeight() - scale * 100) / 2;
            g2d.drawImage(homeTeamPic, WIDTH / 4 - scale * 50, middleY, scale * 100, scale * 100, null);
            g2d.drawImage(awayTeamPic, WIDTH * 3 / 4 - scale * 50, middleY, scale * 100, scale * 100, null);

            String matchTime = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());
            g2d.setColor(Color.WHITE);
            Font font = loadFontFromFile(scale).deriveFont(scale * 30f);
            g2d.setFont(font);
            int textWidth = g2d.getFontMetrics().stringWidth(matchTime);
            int textX = (WIDTH / 2) - (textWidth / 2);
            g2d.drawString(matchTime, textX, middleY + scale * 70);

            g2d.setColor(Color.WHITE);
            g2d.setFont(font);

            String homeTeamCode = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
            int text1Width = g2d.getFontMetrics().stringWidth(homeTeamCode);
            int text1X = WIDTH / 4 - (text1Width / 2);
            int text1Y = middleY + scale * 100 + scale * 30;
            g2d.drawString(homeTeamCode, text1X, text1Y);

            String awayTeamCode = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
            int text2Width = g2d.getFontMetrics().stringWidth(awayTeamCode);
            int text2X = WIDTH * 3 / 4 - (text2Width / 2);
            int text2Y = middleY + scale * 100 + scale * 30;
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
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

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
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("team_colors.json")) {
            if (input != null) {
                JsonNode jsonNode = new ObjectMapper().readTree(input);
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
            log.error(e.getMessage());
        }
    }

    private record TeamColor(Color home, Color away, Color third) {
    }

    private Font loadFontFromFile(int scale) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ClassPathResource("static/pl-bold.ttf").getFile());
        } catch (Exception e) {
            System.err.println("Error loading font: " + e.getMessage());
            return new Font("Arial", Font.BOLD, scale * 30);
        }
    }

    private boolean similarTo(Color home, Color away) {
        int r1 = home.getRed();
        int g1 = home.getGreen();
        int b1 = home.getBlue();

        int r2 = away.getRed();
        int g2 = away.getGreen();
        int b2 = away.getBlue();

        double[] homeCieLab = rgbToCIELAB(r1, g1, b1);
        double[] awayCieLab = rgbToCIELAB(r2, g2, b2);

        double lDiffSquared = (awayCieLab[0] - homeCieLab[0]) * (awayCieLab[0] - homeCieLab[0]);
        double aDiffSquared = (awayCieLab[1] - homeCieLab[1]) * (awayCieLab[1] - homeCieLab[1]);
        double bDiffSquared = (awayCieLab[2] - homeCieLab[2]) * (awayCieLab[2] - homeCieLab[2]);

        double sumSquared = lDiffSquared + aDiffSquared + bDiffSquared;

        double distance = Math.sqrt(sumSquared);

        return distance < 49;
    }

    public static double[] rgbToCIELAB(int red, int green, int blue) {
        double[] xyz = rgbToXYZ(red, green, blue);
        return xyzToCIELAB(xyz[0], xyz[1], xyz[2]);
    }

    private static double[] rgbToXYZ(int red, int green, int blue) {
        // Convert RGB to sRGB
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;

        // Apply sRGB to linear RGB conversion
        r = (r <= 0.04045) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.04045) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.04045) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        r *= 100;
        g *= 100;
        b *= 100;

        // Calculate XYZ values
        double X = r * 0.4124 + g * 0.3577 + b * 0.1805;
        double Y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double Z = r * 0.0193 + g * 0.1192 + b * 0.9505;

        return new double[]{X, Y, Z};
    }

    private static double[] xyzToCIELAB(double x, double y, double z) {
        // Reference white point (D65)
        double xref = 95.044;
        double yref = 100.0;
        double zref = 108.755;

        // Convert XYZ to normalized XYZ
        x /= xref;
        y /= yref;
        z /= zref;

        // Apply non-linear transformation
        x = (x > 0.008856) ? Math.pow(x, 1 / 3.0) : (7.787 * x + 16.0 / 116.0);
        y = (y > 0.008856) ? Math.pow(y, 1 / 3.0) : (7.787 * y + 16.0 / 116.0);
        z = (z > 0.008856) ? Math.pow(z, 1 / 3.0) : (7.787 * z + 16.0 / 116.0);

        // Calculate CIELAB values
        double L = 116.0 * y - 16.0;
        double a = 500.0 * (x - y);
        double b = 200.0 * (y - z);

        return new double[]{L, a, b};
    }
}
