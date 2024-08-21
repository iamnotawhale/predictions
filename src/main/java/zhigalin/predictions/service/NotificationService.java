package zhigalin.predictions.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import zhigalin.predictions.model.football.Team;
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
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    private final Map<Integer, List<String>> notificationBLackList = new HashMap<>();

    public NotificationService(UserService userService, MatchService matchService, PredictionService predictionService) {
        this.userService = userService;
        this.matchService = matchService;
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
                                if (minutes == 30) {
                                    sendPhoto(notification);
                                } else {
                                    sendNotification(notification);
                                }
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

        MultipartBody body = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .queryString("text", "text")
                .field("photo", new File(Objects.requireNonNull(createImage(match))));
        body.asString();

//        HttpResponse<String> response = Unirest.post(urlPhoto)
//                .headers(Map.of("accept", "application/json",
//                                "content-type", "application/json"
//                        )
//                )
//                .queryString("chat_id", chatId)
//                .body("{\"photo\":\"https://telegra.ph/file/fed7d1625ba24e824955b.jpg\"}")
//                .asString();
//        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
//        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
//        if (response.getStatus() == 200) {
//            log.info("Send photo for match {}:{} notification has been send to {}",
//                    homeTeam.getCode(),
//                    awayTeam.getCode(),
//                    notification.getUser().getLogin()
//            );
//        } else {
//            log.warn("Don't send photo for for match {}:{} not send to {}",
//                    homeTeam.getCode(),
//                    awayTeam.getCode(),
//                    notification.getUser().getLogin()
//            );
//        }
    }

    private void sendNotification(Notification notification) throws UnirestException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        String homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
        String builder = "Не проставлен прогноз на матч\n\n" +
                         homeTeam + " " +
                         match.getLocalDateTime().format(formatter) + " " +
                         awayTeam + " \n\n" +
                         "осталось " + Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes() + " мин.";
        String chatId = notification.getUser().getTelegramId();

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Сделать прогноз")
                .callbackData("/" + homeTeam + ":" + awayTeam)
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(Collections.singleton(List.of(button)))
                .build();

        HttpResponse<String> response = Unirest.get(urlMessage)
                .queryString("chat_id", chatId)
                .queryString("text", builder)
                .queryString("reply_markup", objectMapper.writeValueAsString(markup))
                .queryString("parse_mode", "Markdown")
                .asString();
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
            // Create the base image
            BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            BufferedImage backgroundImage = ImageIO.read(new ClassPathResource("static/img/back.webp").getFile());
            g2d.drawImage(backgroundImage, 0, 0, 512, 256, null);

            // Load the two 100x100 images
            BufferedImage pic1 = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getHomeTeamId() + ".webp").getFile());
            BufferedImage pic2 = ImageIO.read(new ClassPathResource("static/img/teams/" + match.getAwayTeamId() + ".webp").getFile());

            // Calculate vertical middle position
            int middleY = (image.getHeight() - 100) / 2;

            // Draw pic1 on the left with outline
            g2d.drawImage(pic1, 512 / 4 - 50, middleY, 100, 100, null);

            // Draw pic2 on the right with outline
            g2d.drawImage(pic2, 512 * 3 / 4 - 50, middleY, 100, 100, null);

            String text = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());
            g2d.setColor(Color.WHITE);// Set text color
            Font font = new Font("Arial", Font.BOLD, 30); // Choose a font
            g2d.setFont(font);
            int textWidth = g2d.getFontMetrics().stringWidth(text);
            int textX = (512 / 2) - (textWidth / 2);
            g2d.drawString(text, textX, middleY + 50); // Position the text

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

            // Create a temporary file in the system's temporary directory
            File tempFile = File.createTempFile("combined", ".png");
            ImageIO.write(image, "png", tempFile);

            // Return the path to the created image
            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            log.error("Error creating image: {}", e.getMessage());
            return null;
        }

    }

}
