package zhigalin.predictions.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.user.UserService;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationService {
    @Value("${bot.url}")
    private String url;

    private final UserService userService;
    private final MatchService matchService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    private final List<Notification> notificationsToSend = new ArrayList<>();
    private final List<String> sentNotifications = new ArrayList<>();

    public void check() throws UnirestException {
        List<User> users = userService.findAll();
        List<Match> nearest = matchService.findAllNearest();
        if (!nearest.isEmpty()) {
            users.forEach(user -> {
                for (Match match : nearest) {
                    boolean hasPredict = match.getPredictions().stream()
                            .anyMatch(prediction -> prediction.getUser().getId().equals(user.getId()));
                    if (!hasPredict) {
                        Notification notification = Notification.builder().user(user).match(match).build();
                        if (!notificationsToSend.contains(notification)) {
                            notificationsToSend.add(notification);
                        } else {
                            notificationsToSend.remove(notification);
                        }
                    }
                }
            });
            sendNotification(notificationsToSend);
        } else {
         notificationsToSend.clear();
         sentNotifications.clear();
        }
    }

    public void sendNotification(List<Notification> list) throws UnirestException {
        for (Notification notification : list) {
            if (!sentNotifications.contains(String.valueOf(notification.getMatch().getId()) + notification.getUser().getId())) {
                Duration duration = Duration.between(LocalTime.now().plusMinutes(6), notification.getMatch().getLocalDateTime().toLocalTime());
                StringBuilder builder = new StringBuilder();
                builder.append("Не проставлен прогноз на матч:\n")
                        .append(notification.getMatch().getHomeTeam().getCode()).append(" ")
                        .append(notification.getMatch().getLocalDateTime().format(formatter)).append(" ")
                        .append(notification.getMatch().getAwayTeam().getCode()).append(" ")
                        .append("осталось ").append(duration.toMinutes() % 60).append("мин.");
                try {
                    String chatId = notification.getUser().getTelegramId();

                    HttpResponse<JsonNode> response = Unirest.get(url)
                            .queryString("chat_id", chatId)
                            .queryString("text", builder.toString())
                            .queryString("parse_mode", "Markdown")
                            .asJson();
                    if (response.getStatus() == 200) {
                        log.info("Not predictable match {}:{} notification has been send to {}",
                                notification.getMatch().getHomeTeam().getCode(),
                                notification.getMatch().getAwayTeam().getCode(),
                                notification.getUser().getLogin()
                        );
                        sentNotifications.add(String.valueOf(notification.getMatch().getId()) + notification.getUser().getId());
                    } else {
                        log.warn("Don't send not predictable match notification");
                    }
                } catch (UnirestException e) {
                    log.error("Sending message error: " + e.getMessage());
                    throw e;
                }
            }
        }
    }
}
