package zhigalin.predictions.service.notification;

import java.time.Duration;
import java.time.LocalDate;
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
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Player;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.api.ApiClient;
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
    private final ApiClient api;
    private final PanicSender panicSender;
    private final ObjectMapper objectMapper;

    private static final int[] REMINDER_MINUTES_BEFORE = {60, 40, 20};

    private final Set<String> notificationBlackList = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> liveScoreNotifyAt = new ConcurrentHashMap<>();
    private final Set<Integer> weeklyResultsSent = ConcurrentHashMap.newKeySet();
    private static final long LIVE_SCORE_DEDUP_MS = 60_000L;

    public NotificationService(MatchService matchService,
                               PredictionService predictionService,
                               OddsService oddsService,
                               ImageRenderer images,
                               ChartRenderer charts,
                               ApiClient api,
                               PanicSender panicSender,
                               ObjectMapper objectMapper) {
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.oddsService = oddsService;
        this.images = images;
        this.charts = charts;
        this.api = api;
        this.panicSender = panicSender;
        this.objectMapper = objectMapper;
    }

    public boolean sendTodayMatchNotification() {
        return sendTodayMatchNotification(LocalDate.now());
    }

    public boolean sendTodayMatchNotification(LocalDate date) {
        log.info("Send today match notification for {}", date);
        List<Match> matches = matchService.findAllByDate(date);
        if (matches.isEmpty()) {
            return false;
        }

        oddsService.oddsInit2(matches);

        List<MatchRecord> list = matches.stream()
                .map(m -> new MatchRecord(m.getHomeTeamId(), m.getAwayTeamId(), m.getWeekId(), m.getLocalDateTime()))
                .sorted(Comparator.comparingInt(MatchRecord::weekId).thenComparing(MatchRecord::localDateTime))
                .toList();

        String path = images.createTodayMatchesImage(list);
        if (path != null) {
            String caption = date.equals(LocalDate.now())
                    ? "Сегодняшние матчи"
                    : "Матчи на " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date);
            api.sendPhoto(defaultChatId, caption, path, null);
            return true;
        }
        return false;
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
            api.sendPhoto(defaultChatId, "Матч " + homeTeam.getCode() + "-" + awayTeam.getCode() + " окончен", path, null);
        }
    }

    public void sendWeeklyResults() {
        log.info("Send weekly results");
        int weekId = DaoUtil.currentWeekId;
        if (!weeklyResultsSent.add(weekId)) {
            return;
        }
        Map<String, Integer> usersPoints = predictionService.getWeeklyUsersPoints(weekId);
        String path = images.createWeeklyImage(weekId, usersPoints);
        if (path != null) {
            api.sendPhoto(defaultChatId, "Результаты " + weekId + " тура", path, null);
        }
        if (!usersPoints.isEmpty()) {
            StringBuilder text = new StringBuilder("*Зачёт ").append(weekId).append(" тура*\n");
            usersPoints.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> text.append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
            api.sendMessage(defaultChatId, text.toString(), null);
        }
        sendTotalPointsChart();
    }

    public void sendLiveScoreUpdate(Match match, Integer prevHome, Integer prevAway) {
        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        if (home == null || away == null) {
            return;
        }
        String key = String.valueOf(match.getPublicId());
        long now = System.currentTimeMillis();
        Long last = liveScoreNotifyAt.get(key);
        if (last != null && now - last < LIVE_SCORE_DEDUP_MS) {
            return;
        }
        liveScoreNotifyAt.put(key, now);
        String prev = (prevHome == null ? "-" : prevHome) + ":" + (prevAway == null ? "-" : prevAway);
        String next = match.getHomeTeamScore() + ":" + match.getAwayTeamScore();
        String text = "⚽ *" + home.getCode() + " " + next + " " + away.getCode() + "*\n"
                      + prev + " → " + next;
        api.sendMessage(defaultChatId, text, null);
    }

    public void sendTotalPointsChart() {
        String path = charts.createTotalPointsChartImage();
        if (path != null) {
            api.sendPhoto(defaultChatId, "График набора очков", path, null);
        }
    }

    public void checkReminders() {
        LocalDateTime now = LocalDateTime.now();
        int horizonMinutes = REMINDER_MINUTES_BEFORE[0] + 1;
        List<Match> upcoming = matchService.findAllNearest(horizonMinutes).stream()
                .filter(m -> Objects.equals(m.getStatus(), "ns") && !Objects.equals(m.getStatus(), "pst"))
                .toList();

        if (upcoming.isEmpty()) {
            notificationBlackList.clear();
            return;
        }

        for (Match match : upcoming) {
            long minutesLeft = Duration.between(now, match.getLocalDateTime()).toMinutes();
            for (int reminderMinutes : REMINDER_MINUTES_BEFORE) {
                if (!isReminderWindow(minutesLeft, reminderMinutes)) {
                    continue;
                }
                Map<Integer, List<Lineup>> lineups = api.getLineups(match.getPublicId());
                List<Prediction> matchPredicts = predictionService.getByMatchPublicId(match.getPublicId());
                for (User user : DaoUtil.USERS.values()) {
                    boolean hasPredict = matchPredicts.stream()
                            .anyMatch(p -> p.getUserId() == user.getId());
                    if (hasPredict) {
                        continue;
                    }
                    String key = user.getId() + ":" + match.getPublicId() + ":" + reminderMinutes;
                    if (!notificationBlackList.add(key)) {
                        continue;
                    }
                    Notification notification = Notification.builder()
                            .user(user)
                            .match(match)
                            .lineups(lineups)
                            .build();
                    log.info("Predict reminder: {} min before match, user={}, match={}",
                            reminderMinutes, user.getId(), match.getPublicId());
                    sendNotification(notification);
                }
            }
        }
    }

    /** Окно для проверки раз в 30 с: напоминание «за N минут» срабатывает при N…N-1 мин до старта. */
    private static boolean isReminderWindow(long minutesLeft, int reminderMinutes) {
        return minutesLeft <= reminderMinutes && minutesLeft > reminderMinutes - 2;
    }

    private void sendNotification(Notification notification) {
        log.info("Send notification");
        try {
            Match match = matchService.findByPublicId(notification.getMatch().getPublicId());
            long minutesLeft = Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes();
            String chatId = notification.getUser().getTelegramId();

            if (chatId != null && !chatId.isEmpty()) {

                Team home = DaoUtil.team(match.getHomeTeamId());
                Team away = DaoUtil.team(match.getAwayTeamId());
                if (home == null || away == null) {
                    return;
                }
                String homeTeam = home.getCode();
                String awayTeam = away.getCode();

                Map<String, Integer> sorting = Map.of("G", 1, "D", 2, "M", 3, "F", 4);
                Comparator<Lineup> lineupComparator = Comparator.comparingInt(
                        l -> sorting.getOrDefault(l.getPlayer().getPos(), 99)
                );

                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text("Сделать прогноз")
                        .callbackData("/" + homeTeam + ":" + awayTeam + "_")
                        .build();
                InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                        .keyboard(Collections.singleton(List.of(button)))
                        .build();

                String matchTime = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());
                StringBuilder caption = new StringBuilder()
                        .append("Не проставлен прогноз на матч").append("\n")
                        .append("Осталось ").append(minutesLeft)
                        .append(minutesLeft % 10 == 1 ? " минута" : minutesLeft > 20 && List.of(2L, 3L, 4L).contains(minutesLeft % 10) ? " минуты" : " минут");

                Map<Integer, List<Lineup>> lineups = notification.getLineups();
                List<Lineup> homeLineup = lineups != null
                        ? lineups.getOrDefault(match.getHomeTeamId(), List.of())
                        : List.of();
                List<Lineup> awayLineup = lineups != null
                        ? lineups.getOrDefault(match.getAwayTeamId(), List.of())
                        : List.of();
                if (!homeLineup.isEmpty() || !awayLineup.isEmpty()) {
                    List<String> homeLineups = homeLineup.stream()
                            .sorted(lineupComparator)
                            .map(this::getFormattedName)
                            .toList();

                    List<String> awayLineups = awayLineup.stream()
                            .sorted(lineupComparator)
                            .map(this::getFormattedName)
                            .toList();

                    int maxLength = 0;
                    for (String player : homeLineups) {
                        if (player.length() > maxLength) {
                            maxLength = player.length();
                        }
                    }

                    caption.append("\n\n")
                            .append("`")
                            .append(padRight(homeTeam, maxLength)).append("  ")
                            .append(awayTeam)
                            .append("`").append("\n");
                    for (int i = 0; i < homeLineups.size(); i++) {
                        String left = homeLineups.get(i);
                        String right = i < awayLineups.size() ? awayLineups.get(i) : "";
                        caption.append("`").append(padRight(left, maxLength)).append("  ").append(right).append("`").append("\n");
                    }
                }

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
                    api.sendPhoto(chatId, caption.toString(), path, replyMarkupJson);
                }
            }
        } catch (Exception e) {
            panicSender.sendPanic("Sending reminder error", e);
            log.error("Sending reminder error: {}", e.getMessage());
        }
    }

    private String getFormattedName(Lineup l) {
        Player player = l.getPlayer();
        String number = String.valueOf(player.getNumber());
        if (player.getName().contains(" ")) {
            String[] name = player.getName().split(" ");
            return padRight(number + ". ", 4)  + name[name.length - 1];
        } else {
            return padRight(number + ". ", 4) + player.getName();
        }
    }

    private static String padRight(String text, int length) {
        return String.format("%-" + length + "s", text);
    }
}
