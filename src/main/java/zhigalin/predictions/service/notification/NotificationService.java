package zhigalin.predictions.service.notification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Player;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.recommender.BettingRecommendationService;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.notification.NotificationDedupDao;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.api.ApiClient.GoalScorer;
import zhigalin.predictions.util.EspnKitColors;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger("server");

    @Value("${bot.chatId}")
    private String defaultChatId;

    @Value("${notification.reminder.widget.enabled:false}")
    private boolean reminderWidgetEnabled;

    @Value("${notification.reminder.widget.telegram-id:}")
    private String reminderWidgetTelegramId;

    @Value("${miniapp.dev-telegram-id:}")
    private String miniappDevTelegramId;

    private final MatchService matchService;
    private final CupMatchDao cupMatchDao;
    private final PredictionService predictionService;
    private final OddsService oddsService;
    private final HtmlImageRenderer htmlImages;
    private final GoalEventGifRenderer goalGifRenderer;
    private final ApiClient api;
    private final PanicSender panicSender;
    private final ObjectMapper objectMapper;
    private final BettingRecommendationService bettingRecommendationService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final ReminderWidgetFactory reminderWidgetFactory;

    private static final int[] REMINDER_MINUTES_BEFORE = {60, 40, 20};

    private final LiveScoreState liveScoreState = new LiveScoreState();
    private final NotificationDedupDao notificationDedupDao;

    public NotificationService(MatchService matchService,
                               CupMatchDao cupMatchDao,
                               PredictionService predictionService,
                               OddsService oddsService,
                               HtmlImageRenderer htmlImages,
                               GoalEventGifRenderer goalGifRenderer,
                               ApiClient api,
                               PanicSender panicSender,
                               ObjectMapper objectMapper,
                               NotificationDedupDao notificationDedupDao,
                               BettingRecommendationService bettingRecommendationService,
                               UserWeekBonusMatchService userWeekBonusMatchService,
                               ReminderWidgetFactory reminderWidgetFactory) {
        this.matchService = matchService;
        this.cupMatchDao = cupMatchDao;
        this.predictionService = predictionService;
        this.oddsService = oddsService;
        this.htmlImages = htmlImages;
        this.goalGifRenderer = goalGifRenderer;
        this.api = api;
        this.panicSender = panicSender;
        this.objectMapper = objectMapper;
        this.notificationDedupDao = notificationDedupDao;
        this.bettingRecommendationService = bettingRecommendationService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.reminderWidgetFactory = reminderWidgetFactory;
    }

    public boolean sendTodayMatchNotification() {
        return sendTodayMatchNotification(LocalDate.now());
    }

    public boolean sendTodayMatchNotification(LocalDate date) {
        log.info("Send today match notification for {}", date);
        List<Match> eplMatches = matchService.findAllByDate(date);
        List<MatchRecord> list = buildTodayMatchRecords(date, eplMatches);
        if (list.isEmpty()) {
            return false;
        }

        if (!eplMatches.isEmpty()) {
            oddsService.oddsInit2(eplMatches);
        }

        String path = htmlImages.createTodayMatchesImage(list);
        if (path != null) {
            String caption = date.equals(LocalDate.now())
                    ? "Сегодняшние матчи"
                    : "Матчи на " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date);
            api.sendPhoto(defaultChatId, caption, path, null);
            return true;
        }
        return false;
    }

    private List<MatchRecord> buildTodayMatchRecords(LocalDate date, List<Match> eplMatches) {
        List<MatchRecord> list = new java.util.ArrayList<>();
        if (eplMatches != null) {
            for (Match m : eplMatches) {
                list.add(MatchRecord.fromEpl(m));
            }
        }
        for (CupMatch cup : cupMatchDao.findAllByDate(date)) {
            list.add(MatchRecord.fromCup(cup));
        }
        list.sort(Comparator
                .comparing(MatchRecord::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(MatchRecord::id));
        return list;
    }

    public void sendFullTime(Match match) {
        log.info("Send full time match notification");
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());

        predictionService.updateByMatch(match);
        bettingRecommendationService.freezeAtKickoffIfNeeded(match.getId());
        String aiKickoffScore = bettingRecommendationService.kickoffScore(match.getId())
                .map(score -> score[0] + ":" + score[1])
                .orElse(null);

        String center = match.getHomeScore() + ":" + match.getAwayScore();
        List<Prediction> predictions = predictionService.getByMatchPublicId(match.getId());
        predictions.sort(Comparator.comparingInt(Prediction::getPoints).reversed());
        List<Result> results = predictions.stream()
                .map(p -> {
                    User user = DaoUtil.USERS.get(p.getUserId());
                    String predict = (p.getHomeScore() != null ? p.getHomeScore() : "") + ":" +
                                     (p.getAwayScore() != null ? p.getAwayScore() : "");
                    boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                            p.getUserId(), match.getWeekId(), match.getId());
                    return new Result(user.getLogin().substring(0, 3), predict, p.getPoints(), weekBonus);
                })
                .sorted(Comparator.comparingInt(Result::point).reversed().thenComparing(Result::login))
                .toList();

        String path = htmlImages.createEplResultImage(
                match.getId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                center,
                results,
                aiKickoffScore
        );

        if (path != null) {
            api.sendPhoto(defaultChatId, "Матч " + homeTeam.getCode() + "-" + awayTeam.getCode() + " окончен", path, null);
        }
        api.evictLineups(match.getId());
        removeLiveScoreNoise(match);
    }

    /** Deletes live score GIF/text for the match from the chat and clears local/DB ids. */
    public void removeLiveScoreNoise(Match match) {
        if (match == null) {
            return;
        }
        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        String key = liveScoreState.key(match, home, away);
        Integer messageId = liveScoreState.existingMessageId(match, key, home, away);
        if (messageId != null) {
            api.deleteMessage(defaultChatId, messageId);
        }
        matchService.updateLiveScoreMessageId(match.getId(), null);
        liveScoreState.clear(match, home, away);
    }

    public void sendWeeklyResults() {
        log.info("Send weekly results");
        int weekId = DaoUtil.currentWeekId;
        if (!notificationDedupDao.tryMarkWeeklyResultsSent(weekId)) {
            return;
        }
        Map<String, Integer> usersPoints = predictionService.getWeeklyUsersPoints(weekId);
        String path = htmlImages.createWeeklyImage(weekId, usersPoints);
        if (path != null) {
            api.sendPhoto(defaultChatId, "Результаты " + weekId + " тура", path, null);
        }
    }

    public void sendLiveScoreUpdate(Match match, Integer prevHome, Integer prevAway) {
        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        if (home == null || away == null) {
            return;
        }
        String key = liveScoreState.key(match, home, away);
        String text = buildLiveScoreUpdateText(match, home, away, prevHome, prevAway);
        if (text.equals(liveScoreState.lastText(key))) {
            return;
        }
        Integer existingMessageId = liveScoreState.existingMessageId(match, key, home, away);
        Integer gifMessageId = trySendOrEditGoalEventGif(
                match, home, away, prevHome, prevAway, text, existingMessageId);
        if (gifMessageId != null) {
            if (existingMessageId != null && !existingMessageId.equals(gifMessageId)) {
                api.deleteMessage(defaultChatId, existingMessageId);
            }
            liveScoreState.putMessageId(key, gifMessageId);
            liveScoreState.markAnimation(key);
            matchService.updateLiveScoreMessageId(match.getId(), gifMessageId);
            liveScoreState.putLastText(key, text);
            return;
        }
        liveScoreState.unmarkAnimation(key);
        boolean delivered = false;
        if (existingMessageId != null) {
            delivered = api.editMessageText(defaultChatId, existingMessageId, text, null);
            if (!delivered) {
                api.deleteMessage(defaultChatId, existingMessageId);
                liveScoreState.removeMessageId(key);
            }
        }
        if (!delivered) {
            Integer sentMessageId = api.sendMessageAndGetId(defaultChatId, text, null);
            if (sentMessageId != null) {
                liveScoreState.putMessageId(key, sentMessageId);
                matchService.updateLiveScoreMessageId(match.getId(), sentMessageId);
                delivered = true;
            }
        }
        if (delivered) {
            liveScoreState.putLastText(key, text);
        }
    }

    private Integer trySendOrEditGoalEventGif(
            Match match,
            Team home,
            Team away,
            Integer prevHome,
            Integer prevAway,
            String caption,
            Integer existingMessageId
    ) {
        if (!goalGifRenderer.isEnabled()) {
            return null;
        }
        int ph = prevHome == null ? 0 : prevHome;
        int pa = prevAway == null ? 0 : prevAway;
        int nh = match.getHomeScore() == null ? 0 : match.getHomeScore();
        int na = match.getAwayScore() == null ? 0 : match.getAwayScore();
        int prevTotal = ph + pa;
        int nextTotal = nh + na;
        if (prevTotal == nextTotal) {
            return null;
        }
        boolean disallowed = nextTotal < prevTotal;
        GoalScorer latest = disallowed ? null : resolveLatestScorer(match, nextTotal);
        String minute = disallowed ? liveClock(match)
                : latest != null && latest.minute() != null && !latest.minute().isBlank() ? latest.minute() : null;
        String subtitle = disallowed
                ? "VAR  ·  offside" + (minute == null ? "" : "  " + minute)
                : scorerLabel(latest);
        String path = null;
        try {
            EspnKitColors.Kit[] kits = matchKits(match);
            path = goalGifRenderer.renderToTempFile(new GoalEventGifRenderer.Request(
                    home.getId(),
                    away.getId(),
                    home.getCode(),
                    away.getCode(),
                    ph, pa, nh, na,
                    subtitle,
                    disallowed,
                    minute,
                    kits[0],
                    kits[1]
            ));
            if (path == null) {
                return null;
            }
            if (existingMessageId != null
                    && api.editMessageMediaAnimation(defaultChatId, existingMessageId, caption, path, null)) {
                return existingMessageId;
            }
            return api.sendAnimation(defaultChatId, caption, path, null);
        } catch (Exception e) {
            log.warn("Goal GIF send failed: {}", e.getMessage());
            return null;
        } finally {
            if (path != null) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(path));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private GoalScorer resolveLatestScorer(Match match, int expectedTotal) {
        if (expectedTotal <= 0 || match.getEspnId() == null || match.getEspnId().isBlank()) {
            return null;
        }
        try {
            List<GoalScorer> scorers = api.listGoalScorers(match.getEspnId(), expectedTotal);
            return scorers.isEmpty() ? null : scorers.get(scorers.size() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    private EspnKitColors.Kit[] matchKits(Match match) {
        if (match.getEspnId() == null || match.getEspnId().isBlank()) {
            return new EspnKitColors.Kit[2];
        }
        try {
            var summary = api.fetchEspnSummary(match.getEspnId());
            return new EspnKitColors.Kit[]{
                    EspnKitColors.load(summary, "home"),
                    EspnKitColors.load(summary, "away")
            };
        } catch (Exception e) {
            log.debug("Kit colors unavailable for {}: {}", match.getEspnId(), e.getMessage());
            return new EspnKitColors.Kit[2];
        }
    }

    private static String liveClock(Match match) {
        String status = match.getStatus();
        return status != null && status.matches("\\d+'?(\\+\\d+'?)?") ? status : null;
    }

    private static String scorerLabel(GoalScorer last) {
        if (last == null) {
            return null;
        }
        if (last.minute() == null || last.minute().isBlank()) {
            return last.scorer();
        }
        return last.scorer() + "  " + last.minute();
    }

    private String buildLiveScoreUpdateText(Match match, Team home, Team away, Integer prevHome, Integer prevAway) {
        String next = match.getHomeScore() + ":" + match.getAwayScore();
        StringBuilder text = new StringBuilder();
        text.append("⚽ *")
                .append(home.getCode())
                .append(" ")
                .append(next)
                .append(" ")
                .append(away.getCode())
                .append("*\n")
                .append(prevHome == null ? "-" : prevHome)
                .append(":")
                .append(prevAway == null ? "-" : prevAway)
                .append(" → ")
                .append(next);

        int expectedTotal = (match.getHomeScore() == null ? 0 : match.getHomeScore())
                + (match.getAwayScore() == null ? 0 : match.getAwayScore());
        if (expectedTotal > 0 && match.getEspnId() != null && !match.getEspnId().isBlank()) {
            List<GoalScorer> scorers = api.listGoalScorers(match.getEspnId(), expectedTotal);
            if (!scorers.isEmpty()) {
                text.append("\nГолы: ");
                text.append(scorers.stream()
                        .map(GoalScorer::formatForCaption)
                        .collect(Collectors.joining(", ")));
            }
        }
        return text.toString();
    }

    private static final int REMINDER_VISIBLE_AFTER_KICKOFF_MINUTES = 5;

    public void checkReminders() {
        LocalDateTime now = LocalDateTime.now();
        int horizonMinutes = REMINDER_MINUTES_BEFORE[0] + 1;
        List<Match> upcoming = matchService.findAllNearest(horizonMinutes).stream()
                .filter(m -> Objects.equals(m.getStatus(), "ns"))
                .toList();

        for (Match match : upcoming) {
            long minutesLeft = Duration.between(now, match.getStartsAt()).toMinutes();
            for (int reminderMinutes : REMINDER_MINUTES_BEFORE) {
                if (!ReminderCaption.isReminderWindow(minutesLeft, reminderMinutes)) {
                    continue;
                }
                ApiClient.EspnLineups espnLineups = api.getEspnLineups(
                        match.getEspnId(),
                        match.getHomeTeamId(),
                        match.getAwayTeamId()
                );
                Map<Integer, List<Lineup>> lineups = espnLineups.byTeam();
                String homeFormation = espnLineups.homeFormation();
                String awayFormation = espnLineups.awayFormation();
                if (lineups.isEmpty()) {
                    lineups = api.getLineups(match.getId());
                }
                Set<Integer> predictedUserIds = predictionService.getByMatchPublicId(match.getId()).stream()
                        .map(Prediction::getUserId)
                        .collect(Collectors.toSet());
                String imagePathPlain = null;
                String imagePathBonus = null;
                String matchTime = DateTimeFormatter.ofPattern("HH:mm").format(match.getStartsAt());
                for (User user : DaoUtil.USERS.values()) {
                    if (predictedUserIds.contains(user.getId())) {
                        continue;
                    }
                    if (!notificationDedupDao.tryMarkReminderSent(user.getId(), match.getId(), reminderMinutes)) {
                        continue;
                    }
                    boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                            user.getId(), match.getWeekId(), match.getId());
                    Notification notification = Notification.builder()
                            .user(user)
                            .match(match)
                            .lineups(lineups)
                            .build();
                    log.info("Predict reminder: {} min before match, user={}, match={}, weekBonus={}",
                            reminderMinutes, user.getId(), match.getId(), weekBonus);
                    Integer prevMessageId = notificationDedupDao.findLatestReminderTelegramMessageId(
                            user.getId(), match.getId());
                    Integer messageId = null;
                    if (useReminderWidget(user)) {
                        messageId = sendReminderWidget(
                                notification, prevMessageId, minutesLeft, matchTime,
                                homeFormation, awayFormation);
                    }
                    if (messageId == null) {
                        String imagePath;
                        if (weekBonus) {
                            if (imagePathBonus == null) {
                                imagePathBonus = htmlImages.createReminderImage(
                                        match.getId(),
                                        match.getHomeTeamId(),
                                        match.getAwayTeamId(),
                                        matchTime,
                                        true
                                );
                            }
                            imagePath = imagePathBonus;
                        } else {
                            if (imagePathPlain == null) {
                                imagePathPlain = htmlImages.createReminderImage(
                                        match.getId(),
                                        match.getHomeTeamId(),
                                        match.getAwayTeamId(),
                                        matchTime,
                                        false
                                );
                            }
                            imagePath = imagePathPlain;
                        }
                        if (prevMessageId != null
                                && user.getTelegramId() != null
                                && !user.getTelegramId().isBlank()) {
                            api.deleteMessage(user.getTelegramId(), prevMessageId);
                        }
                        messageId = sendNotification(notification, imagePath);
                    }
                    notificationDedupDao.saveReminderTelegramMessageId(
                            user.getId(), match.getId(), reminderMinutes, messageId);
                }
            }
        }
        deleteExpiredReminders();
    }

    /** Prediction window closes 5 minutes after kickoff; a reminder asking to predict is stale after that. */
    private void deleteExpiredReminders() {
        for (NotificationDedupDao.ExpiredReminder reminder
                : notificationDedupDao.findExpiredReminders(REMINDER_VISIBLE_AFTER_KICKOFF_MINUTES)) {
            User user = DaoUtil.USERS.values().stream()
                    .filter(u -> u.getId() == reminder.userId())
                    .findFirst()
                    .orElse(null);
            if (user != null && user.getTelegramId() != null && !user.getTelegramId().isBlank()) {
                api.deleteMessage(user.getTelegramId(), reminder.messageId());
            }
            // Mark even when Telegram refuses (message already gone / older than 48h) so we do not retry forever.
            notificationDedupDao.markReminderMessageDeleted(
                    reminder.userId(), reminder.matchId(), reminder.reminderMinutes());
        }
    }

    private boolean useReminderWidget(User user) {
        if (!reminderWidgetEnabled || user == null) {
            return false;
        }
        String target = reminderWidgetTelegramId;
        if (target == null || target.isBlank()) {
            target = miniappDevTelegramId;
        }
        if (target == null || target.isBlank()) {
            return false;
        }
        String telegramId = user.getTelegramId();
        return telegramId != null && !telegramId.isBlank() && telegramId.equals(target.trim());
    }

    private Integer sendReminderWidget(
            Notification notification,
            Integer prevMessageId,
            long minutesLeft,
            String matchTime,
            String homeFormation,
            String awayFormation
    ) {
        try {
            Match match = notification.getMatch();
            String chatId = notification.getUser().getTelegramId();
            if (chatId == null || chatId.isBlank()) {
                return null;
            }
            Map<Integer, List<Lineup>> lineups = notification.getLineups();
            ReminderWidgetFactory.Payload payload = reminderWidgetFactory.build(
                    match,
                    notification.getUser().getId(),
                    minutesLeft,
                    matchTime,
                    null,
                    lineups,
                    homeFormation,
                    awayFormation);
            if (prevMessageId != null) {
                api.deleteMessage(chatId, prevMessageId);
            }
            Integer messageId = api.sendWidget(
                    chatId,
                    payload.html(),
                    payload.chatData(),
                    null,
                    null,
                    null,
                    true
            );
            if (messageId != null) {
                ReminderWidgetFactory.rememberLineups(
                        chatId, messageId, lineups, homeFormation, awayFormation);
            }
            return messageId;
        } catch (Exception e) {
            panicSender.sendPanic("Sending reminder widget error", e);
            log.error("Sending reminder widget error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Admin/dev: send reminder widget for the nearest upcoming EPL match without touching
     * {@code notification_reminder_sent}. Prefer matches the user has not predicted yet.
     */
    public Integer sendReminderWidgetDemo(String telegramChatId) {
        if (telegramChatId == null || telegramChatId.isBlank()) {
            return null;
        }
        User user = DaoUtil.USERS.values().stream()
                .filter(u -> telegramChatId.equals(u.getTelegramId()))
                .findFirst()
                .orElse(null);
        if (user == null) {
            log.warn("widget demo: no user for telegramId={}", telegramChatId);
            return null;
        }
        List<Match> upcoming = matchService.findAllByUpcomingDays(14);
        if (upcoming == null || upcoming.isEmpty()) {
            upcoming = matchService.findAllByCurrentWeek();
        }
        if (upcoming == null || upcoming.isEmpty()) {
            log.warn("widget demo: no upcoming matches");
            return null;
        }
        Match match = upcoming.stream()
                .filter(m -> predictionService.findByMatchIdAndUserId(m.getId(), user.getId()) == null)
                .findFirst()
                .orElse(upcoming.get(0));
        Prediction existing = predictionService.findByMatchIdAndUserId(match.getId(), user.getId());
        String predicted = existing != null
                && existing.getHomeScore() != null
                && existing.getAwayScore() != null
                ? existing.getHomeScore() + ":" + existing.getAwayScore()
                : null;
        String matchTime = match.getStartsAt() == null
                ? "--:--"
                : match.getStartsAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        long minutesLeft = match.getStartsAt() == null
                ? 60
                : Math.max(1, java.time.Duration.between(
                        java.time.LocalDateTime.now(), match.getStartsAt()).toMinutes());
        try {
            Map<Integer, List<Lineup>> lineups = mockLineupsForDemo(
                    match.getHomeTeamId(), match.getAwayTeamId());
            String homeFormation = "4-3-3";
            String awayFormation = "4-2-3-1";
            ReminderWidgetFactory.Payload payload = reminderWidgetFactory.build(
                    match, user.getId(), minutesLeft, matchTime, predicted, lineups,
                    homeFormation, awayFormation);
            int zlib = ReminderWidgetHtml.zlib9(ReminderWidgetHtml.utf8(payload.html())).length;
            log.info("widget demo html zlib={} jpeg={} chatDataBytes={}",
                    zlib,
                    payload.html().contains("data:image/jpeg"),
                    payload.chatData().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            Integer messageId = api.sendWidget(
                    telegramChatId,
                    payload.html(),
                    payload.chatData(),
                    null,
                    null,
                    null,
                    true
            );
            if (messageId != null) {
                ReminderWidgetFactory.rememberLineups(
                        telegramChatId, messageId, lineups, homeFormation, awayFormation);
            }
            log.info("widget demo sent chatId={} matchId={} messageId={}",
                    telegramChatId, match.getId(), messageId);
            return messageId;
        } catch (Exception e) {
            panicSender.sendPanic("Sending reminder widget demo error", e);
            log.error("Sending reminder widget demo error: {}", e.getMessage());
            return null;
        }
    }

    private Integer sendNotification(Notification notification, String imagePath) {
        log.info("Send notification");
        try {
            Match match = notification.getMatch();
            long minutesLeft = Duration.between(LocalDateTime.now(), match.getStartsAt()).toMinutes();
            String chatId = notification.getUser().getTelegramId();

            if (chatId != null && !chatId.isEmpty()) {
                Team home = DaoUtil.team(match.getHomeTeamId());
                Team away = DaoUtil.team(match.getAwayTeamId());
                if (home == null || away == null) {
                    return null;
                }
                String homeTeam = home.getCode();
                String awayTeam = away.getCode();
                String text = buildReminderCaption(notification, homeTeam, awayTeam, minutesLeft);
                String replyMarkupJson = objectMapper.writeValueAsString(predictMarkup(homeTeam, awayTeam));
                if (imagePath != null) {
                    return api.sendPhoto(chatId, text, imagePath, replyMarkupJson);
                }
            }
        } catch (Exception e) {
            panicSender.sendPanic("Sending reminder error", e);
            log.error("Sending reminder error: {}", e.getMessage());
        }
        return null;
    }

    private static InlineKeyboardMarkup predictMarkup(String homeTeam, String awayTeam) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Сделать прогноз")
                .callbackData("/" + homeTeam + ":" + awayTeam + "_")
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(Collections.singleton(List.of(button)))
                .build();
    }

    private String buildReminderCaption(
            Notification notification, String homeTeam, String awayTeam, long minutesLeft
    ) {
        Match match = notification.getMatch();
        StringBuilder caption = new StringBuilder()
                .append("Не проставлен прогноз на матч").append("\n")
                .append("Осталось ").append(minutesLeft)
                .append(minutesLeft % 10 == 1 ? " минута"
                        : minutesLeft > 20 && List.of(2L, 3L, 4L).contains(minutesLeft % 10)
                        ? " минуты" : " минут");

        Map<String, Integer> sorting = Map.of("G", 1, "D", 2, "M", 3, "F", 4);
        Comparator<Lineup> lineupComparator = Comparator.comparingInt(
                l -> sorting.getOrDefault(l.getPlayer().getPos(), 99)
        );

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
                    .map(ReminderCaption::formattedName)
                    .toList();
            List<String> awayLineups = awayLineup.stream()
                    .sorted(lineupComparator)
                    .map(ReminderCaption::formattedName)
                    .toList();

            int maxLength = 0;
            for (String player : homeLineups) {
                if (player.length() > maxLength) {
                    maxLength = player.length();
                }
            }

            caption.append("\n\n")
                    .append("`")
                    .append(ReminderCaption.padRight(homeTeam, maxLength)).append("  ")
                    .append(awayTeam)
                    .append("`").append("\n");
            for (int i = 0; i < homeLineups.size(); i++) {
                String left = homeLineups.get(i);
                String right = i < awayLineups.size() ? awayLineups.get(i) : "";
                caption.append("`")
                        .append(ReminderCaption.padRight(left, maxLength))
                        .append("  ")
                        .append(right)
                        .append("`")
                        .append("\n");
            }
        }

        return ReminderCaption.trimCaption(caption.toString(), 900);
    }

    public static Map<Integer, List<Lineup>> mockLineupsForDemo(int homeTeamId, int awayTeamId) {
        Map<Integer, List<Lineup>> out = new HashMap<>();
        out.put(homeTeamId, mockSide(
                "Ederson", "G", 1, 31,
                "Walker", "D", 2, 2,
                "Dias", "D", 5, 3,
                "Ake", "D", 6, 6,
                "Gvardiol", "D", 3, 24,
                "Rodri", "M", 4, 16,
                "Silva", "M", 10, 20,
                "De Bruyne", "M", 8, 17,
                "Foden", "F", 7, 47,
                "Haaland", "F", 9, 9,
                "Doku", "F", 11, 11
        ));
        out.put(awayTeamId, mockSide(
                "Leno", "G", 1, 1,
                "Tete", "D", 2, 2,
                "Andersen", "D", 5, 5,
                "Bassey", "D", 6, 3,
                "Robinson", "D", 3, 33,
                "Pereira", "M", 8, 18,
                "Berge", "M", 4, 6,
                "Iwobi", "M", 10, 17,
                "Traore", "M", 7, 11,
                "Jimenez", "F", 11, 7,
                "Muniz", "F", 9, 19
        ));
        return out;
    }

    private static List<Lineup> mockSide(Object... namePosPlaceNum) {
        List<Lineup> rows = new ArrayList<>();
        for (int i = 0; i + 3 < namePosPlaceNum.length; i += 4) {
            Player player = new Player();
            player.setName((String) namePosPlaceNum[i]);
            player.setPos((String) namePosPlaceNum[i + 1]);
            player.setFormationPlace((Integer) namePosPlaceNum[i + 2]);
            player.setNumber((Integer) namePosPlaceNum[i + 3]);
            Lineup row = new Lineup();
            row.setPlayer(player);
            rows.add(row);
        }
        return rows;
    }

}
