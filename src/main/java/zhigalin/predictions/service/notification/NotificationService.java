package zhigalin.predictions.service.notification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import zhigalin.predictions.util.DaoUtil;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger("server");

    @Value("${bot.chatId}")
    private String defaultChatId;

    private final MatchService matchService;
    private final PredictionService predictionService;
    private final OddsService oddsService;
    private final ImageRenderer images;
    private final ChartRenderer charts;
    private final TelegramClient telegram;
    private final PanicSender panicSender;
    private final ObjectMapper objectMapper;

    private final Map<Integer, Set<String>> notificationBlackList = Map.of(
            20, ConcurrentHashMap.newKeySet(),
            60, ConcurrentHashMap.newKeySet()
    );

    public NotificationService(MatchService matchService,
                               PredictionService predictionService,
                               OddsService oddsService,
                               ImageRenderer images,
                               ChartRenderer charts,
                               TelegramClient telegram,
                               PanicSender panicSender,
                               ObjectMapper objectMapper) {
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.oddsService = oddsService;
        this.images = images;
        this.charts = charts;
        this.telegram = telegram;
        this.panicSender = panicSender;
        this.objectMapper = objectMapper;
    }

    public void sendTodayMatchNotification() {
        log.info("Send today match notification");
        List<Match> today = matchService.findAllByTodayDate();
        if (today.isEmpty()) return;

        oddsService.oddsInit2(today);

        List<MatchRecord> list = today.stream()
                .map(m -> new MatchRecord(m.getHomeTeamId(), m.getAwayTeamId(), m.getWeekId(), m.getLocalDateTime()))
                .sorted(Comparator.comparingInt(MatchRecord::weekId).thenComparing(MatchRecord::localDateTime))
                .toList();

        String path = images.createTodayMatchesImage(list);
        if (path != null) {
            telegram.sendPhoto(defaultChatId, "Сегодняшние матчи", path, null);
        }
    }

    public void sendFullTime(Match match) {
        log.info("Send full time match notification");
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());

        predictionService.updateByMatch(match);

        String center = match.getHomeTeamScore() + ":" + match.getAwayTeamScore();
        List<Prediction> predictions = predictionService.getByMatchPublicId(match.getPublicId());
        predictions.sort(Comparator.comparingInt(Prediction::getPoints).reversed());
        List<Result> results = predictions.stream()
                .map(p -> {
                    User user = DaoUtil.USERS.get(p.getUserId());
                    String predict = (p.getHomeTeamScore() != null ? p.getHomeTeamScore() : "") + ":" +
                                     (p.getAwayTeamScore() != null ? p.getAwayTeamScore() : "");
                    return new Result(user.getLogin().substring(0, 3), predict, p.getPoints());
                })
                .sorted(Comparator.comparingInt(Result::point).reversed().thenComparing(Result::login))
                .toList();

        String path = images.createImage(
                match.getPublicId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                center,
                NotificationImageMode.RESULT,
                results
        );

        if (path != null) {
            telegram.sendPhoto(defaultChatId, "Матч " + homeTeam.getCode() + "-" + awayTeam.getCode() + " окончен", path, null);
        }
    }

    public void sendWeeklyResults() {
        log.info("Send weekly results");
        int weekId = DaoUtil.currentWeekId;
        Map<String, Integer> usersPoints = predictionService.getWeeklyUsersPoints(weekId);
        String path = images.createWeeklyImage(weekId, usersPoints);
        if (path != null) {
            telegram.sendPhoto(defaultChatId, "Результаты недели", path, null);
        }
    }

    public void sendTotalPointsChart() {
        String path = charts.createTotalPointsChartImage();
        if (path != null) {
            telegram.sendPhoto(defaultChatId, "График набора очков", path, null);
        }
    }

    public void checkReminders() {
        List<Match> ns = matchService.findAllByTodayDate().stream()
                .filter(m -> m.getStatus().equals("ns"))
                .toList();

        if (!ns.isEmpty()) {
            for (Integer minutes : List.of(60, 20)) {
                List<User> users = DaoUtil.USERS.values().stream().toList();
                List<Match> nearest = matchService.findAllNearest(minutes).stream()
                        .filter(m -> !Objects.equals(m.getStatus(), "pst"))
                        .toList();

                if (!nearest.isEmpty()) {
                    log.info("Nearest matches notification start");
                    for (User user : users) {
                        for (Match match : nearest) {
                            boolean hasPredict = predictionService.getByMatchPublicId(match.getPublicId()).stream()
                                    .anyMatch(p -> p.getUserId() == user.getId());
                            if (!hasPredict) {
                                Notification notification = Notification.builder().user(user).match(match).build();
                                String key = user.getId() + ":" + match.getPublicId() + ":" + minutes;
                                if (!notificationBlackList.get(minutes).contains(key)) {
                                    notificationBlackList.get(minutes).add(key);
                                    sendNotification(notification);
                                }
                            }
                        }
                    }
                } else {
                    notificationBlackList.get(minutes).clear();
                }
            }
        }
    }

    private void sendNotification(Notification notification) {
        log.info("Send notification");
        try {
            Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
            long minutesLeft = Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes();
            String chatId = notification.getUser().getTelegramId();

            if (chatId != null && !chatId.isEmpty()) {

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
                String caption = "Не проставлен прогноз на матч\nОсталось " + minutesLeft +
                                 (minutesLeft % 10 == 1 ? " минута" :
                                         minutesLeft > 20 && List.of(2L, 3L, 4L).contains(minutesLeft % 10) ? " минуты" : " минут");

                String path = images.createImage(
                        match.getPublicId(),
                        match.getHomeTeamId(),
                        match.getAwayTeamId(),
                        matchTime,
                        NotificationImageMode.NOTIFICATION,
                        null
                );

                String replyMarkupJson = objectMapper.writeValueAsString(markup);
                if (path != null) {
                    telegram.sendPhoto(chatId, caption, path, replyMarkupJson);
                }
            }
        } catch (Exception e) {
            panicSender.sendPanic("Sending reminder error", e);
            log.error("Sending reminder error: {}", e.getMessage());
        }
    }
}
