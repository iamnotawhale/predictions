package zhigalin.predictions.service;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.user.UserService;

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
    private final Set<Notification> sentNotifications = new HashSet<>();

    public void check() {
        List<User> users = userService.findAll();
        List<Match> nearest = matchService.findAllNearest();
        users.forEach(user -> {
            for (Match match : nearest) {
                boolean hasPredict = match.getPredictions().stream()
                        .anyMatch(prediction -> prediction.getUser().getId().equals(user.getId()));
                if (!hasPredict) {
                    Notification notification = Notification.builder().user(user).match(match).build();
                    if (!notificationsToSend.contains(notification)) {
                        notificationsToSend.add(notification);
                    }
                }
            }
        });
        sendNotification(notificationsToSend);
    }

    public void sendNotification(List<Notification> list) {
        LocalTime now = LocalTime.now();
        for (Notification notification : list) {
            if (!sentNotifications.contains(notification)) {
                Duration duration = Duration.between(now, notification.getMatch().getLocalDateTime().toLocalTime());
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
                    } else {
                        log.warn("Don't send not predictable match notification");
                    }
                } catch (UnirestException e) {
                    log.error("Sending message error: " + e.getMessage());
                }
            }
            sentNotifications.add(notification);
        }
    }
}
