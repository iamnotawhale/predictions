package zhigalin.predictions.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
    private final List<String> notificationBLackList = new ArrayList<>();

    public void check() throws UnirestException {
        List<User> users = userService.findAll();
        List<Match> nearest = matchService.findAllNearest();
        if (!nearest.isEmpty()) {
            for (User user : users) {
                for (Match match : nearest) {
                    boolean hasPredict = match.getPredictions().stream()
                            .anyMatch(prediction -> prediction.getUser().getId().equals(user.getId()));
                    if (!hasPredict) {
                        Notification notification = Notification.builder().user(user).match(match).build();
                        if (!notificationBLackList.contains(notification.toString())) {
                            notificationBLackList.add(notification.toString());
                            sendNotification(notification);
                        }
                    }
                }
            }
        } else {
            notificationBLackList.clear();
        }
    }

    private void sendNotification(Notification notification) throws UnirestException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        Duration duration = Duration.between(LocalTime.now(),
                match.getLocalDateTime().toLocalTime());
        String builder = "Не проставлен прогноз на матч:\n" +
                match.getHomeTeam().getCode() + " " +
                match.getLocalDateTime().format(formatter) + " " +
                match.getAwayTeam().getCode() + " " +
                "осталось " + duration.toMinutes() % 60 + "мин.";
        String chatId = notification.getUser().getTelegramId();

        HttpResponse<JsonNode> response = Unirest.get(url)
                .queryString("chat_id", chatId)
                .queryString("text", builder)
                .queryString("parse_mode", "Markdown")
                .asJson();
        if (response.getStatus() == 200) {
            log.info("Not predictable match {}:{} notification has been send to {}",
                    match.getHomeTeam().getCode(),
                    match.getAwayTeam().getCode(),
                    notification.getUser().getLogin()
            );
        } else {
            log.warn("Don't send not predictable match notification");
        }
    }

    private void sendingPhoto() {
        InputFile inputFile = new InputFile();
    }
}
