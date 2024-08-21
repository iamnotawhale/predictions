package zhigalin.predictions.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
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

    public void check() throws UnirestException, JsonProcessingException {
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
                                    sendNotification(notification, minutes);
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

    private void sendPhoto(Notification notification) throws UnirestException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        String chatId = notification.getUser().getTelegramId();
        HttpResponse<String> response = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .body("{\"photo\":\"https://telegra.ph/file/fed7d1625ba24e824955b.jpg\"}")
                .asString();
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
        if (response.getStatus() == 200) {
            log.info("Send photo for match {}:{} notification has been send to {}",
                    homeTeam.getCode(),
                    awayTeam.getCode(),
                    notification.getUser().getLogin()
            );
        } else {
            log.warn("Don't send photo for for match {}:{} not send to {}",
                    homeTeam.getCode(),
                    awayTeam.getCode(),
                    notification.getUser().getLogin()
            );
        }
    }

    private void sendNotification(Notification notification, int minutes) throws UnirestException, JsonProcessingException {
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
}
