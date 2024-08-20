package zhigalin.predictions.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    public void check() throws UnirestException {
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
        HttpResponse<JsonNode> response = Unirest.post(urlPhoto)
                .headers(Map.of("accept", "application/json",
                                "content-type", "application/json"
                        )
                )
                .queryString("chat_id", chatId)
                .body("{\"photo\":\"https://telegra.ph/file/fed7d1625ba24e824955b.jpg\"}")
                .asJson();
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

    private void sendNotification(Notification notification, int minutes) throws UnirestException {
        Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
        String builder = "Не проставлен прогноз на матч:\n" +
                         homeTeam.getCode() + " " +
                         match.getLocalDateTime().format(formatter) + " " +
                         awayTeam.getCode() + " " +
                         "осталось " + minutes + " мин.";
        String chatId = notification.getUser().getTelegramId();

        HttpResponse<JsonNode> response = Unirest.get(urlMessage)
                .queryString("chat_id", chatId)
                .queryString("text", builder)
                .queryString("parse_mode", "Markdown")
                .asJson();
        if (response.getStatus() == 200) {
            log.info("Not predictable match {}:{} notification has been send to {}",
                    homeTeam.getCode(),
                    awayTeam.getCode(),
                    notification.getUser().getLogin()
            );
        } else {
            log.warn("Don't send not predictable match notification");
        }
    }
}
