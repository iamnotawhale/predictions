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
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Player;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.notification.Notification;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.recommender.BettingRecommendationService;
import zhigalin.predictions.repository.event.BonusMatchDao;
import zhigalin.predictions.repository.notification.NotificationDedupDao;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.api.ApiClient.GoalScorer;
import zhigalin.predictions.service.api.InjuryService;
import zhigalin.predictions.service.api.InjuryService.InjuryInfo;
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

    private final MatchService matchService;
    private final BonusMatchDao bonusMatchDao;
    private final PredictionService predictionService;
    private final OddsService oddsService;
    private final HtmlImageRenderer htmlImages;
    private final ApiClient api;
    private final InjuryService injuryService;
    private final PanicSender panicSender;
    private final ObjectMapper objectMapper;
    private final BettingRecommendationService bettingRecommendationService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;

    private static final int[] REMINDER_MINUTES_BEFORE = {60, 40, 20};

    private final ConcurrentHashMap<String, Integer> liveScoreMessageIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> liveScoreLastTexts = new ConcurrentHashMap<>();
    private final NotificationDedupDao notificationDedupDao;

    public NotificationService(MatchService matchService,
                               BonusMatchDao bonusMatchDao,
                               PredictionService predictionService,
                               OddsService oddsService,
                               HtmlImageRenderer htmlImages,
                               ApiClient api,
                               InjuryService injuryService,
                               PanicSender panicSender,
                               ObjectMapper objectMapper,
                               NotificationDedupDao notificationDedupDao,
                               BettingRecommendationService bettingRecommendationService,
                               UserWeekBonusMatchService userWeekBonusMatchService) {
        this.matchService = matchService;
        this.bonusMatchDao = bonusMatchDao;
        this.predictionService = predictionService;
        this.oddsService = oddsService;
        this.htmlImages = htmlImages;
        this.api = api;
        this.injuryService = injuryService;
        this.panicSender = panicSender;
        this.objectMapper = objectMapper;
        this.notificationDedupDao = notificationDedupDao;
        this.bettingRecommendationService = bettingRecommendationService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
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

    /** Build PNG for the date without sending (EPL + cups). */
    public String renderTodayMatchesImage(LocalDate date) {
        return htmlImages.createTodayMatchesImage(buildTodayMatchRecords(date, matchService.findAllByDate(date)));
    }

    private List<MatchRecord> buildTodayMatchRecords(LocalDate date, List<Match> eplMatches) {
        List<MatchRecord> list = new java.util.ArrayList<>();
        if (eplMatches != null) {
            for (Match m : eplMatches) {
                list.add(MatchRecord.fromEpl(m));
            }
        }
        for (BonusMatch cup : bonusMatchDao.findAllByDate(date)) {
            list.add(MatchRecord.fromCup(cup));
        }
        list.sort(Comparator
                .comparing(MatchRecord::localDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(MatchRecord::publicId));
        return list;
    }

    public void sendFullTime(Match match) {
        log.info("Send full time match notification");
        Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());

        predictionService.updateByMatch(match);
        bettingRecommendationService.freezeAtKickoffIfNeeded(match.getPublicId());
        String aiKickoffScore = bettingRecommendationService.kickoffScore(match.getPublicId())
                .map(score -> score[0] + ":" + score[1])
                .orElse(null);

        String center = match.getHomeTeamScore() + ":" + match.getAwayTeamScore();
        List<Prediction> predictions = predictionService.getByMatchPublicId(match.getPublicId());
        predictions.sort(Comparator.comparingInt(Prediction::getPoints).reversed());
        List<Result> results = predictions.stream()
                .map(p -> {
                    User user = DaoUtil.USERS.get(p.getUserId());
                    String predict = (p.getHomeTeamScore() != null ? p.getHomeTeamScore() : "") + ":" +
                                     (p.getAwayTeamScore() != null ? p.getAwayTeamScore() : "");
                    boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                            p.getUserId(), match.getWeekId(), match.getPublicId());
                    return new Result(user.getLogin().substring(0, 3), predict, p.getPoints(), weekBonus);
                })
                .sorted(Comparator.comparingInt(Result::point).reversed().thenComparing(Result::login))
                .toList();

        String path = htmlImages.createEplResultImage(
                match.getPublicId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                center,
                results,
                aiKickoffScore
        );

        if (path != null) {
            api.sendPhoto(defaultChatId, "Матч " + homeTeam.getCode() + "-" + awayTeam.getCode() + " окончен", path, null);
        }
        api.evictLineups(match.getPublicId());
        matchService.updateLiveScoreMessageId(match.getPublicId(), null);
        clearLiveScoreState(match);
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
        String key = buildLiveScoreKey(match, home, away);
        String text = buildLiveScoreUpdateText(match, home, away, prevHome, prevAway);
        if (text.equals(liveScoreLastTexts.get(key))) {
            return;
        }
        Integer existingMessageId = resolveExistingLiveScoreMessageId(match, key, home, away);
        boolean delivered = false;
        if (existingMessageId != null) {
            delivered = api.editMessageText(defaultChatId, existingMessageId, text, null);
            if (!delivered) {
                liveScoreMessageIds.remove(key);
            }
        }
        if (!delivered) {
            Integer sentMessageId = api.sendMessageAndGetId(defaultChatId, text, null);
            if (sentMessageId != null) {
                liveScoreMessageIds.put(key, sentMessageId);
                matchService.updateLiveScoreMessageId(match.getPublicId(), sentMessageId);
                delivered = true;
            }
        }
        if (delivered) {
            liveScoreLastTexts.put(key, text);
        }
    }

    private String buildLiveScoreUpdateText(Match match, Team home, Team away, Integer prevHome, Integer prevAway) {
        String next = match.getHomeTeamScore() + ":" + match.getAwayTeamScore();
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

        int expectedTotal = (match.getHomeTeamScore() == null ? 0 : match.getHomeTeamScore())
                + (match.getAwayTeamScore() == null ? 0 : match.getAwayTeamScore());
        if (expectedTotal > 0 && match.getEspnId() != null && !match.getEspnId().isBlank()) {
            List<GoalScorer> scorers = api.listGoalScorers(match.getEspnId(), expectedTotal);
            if (!scorers.isEmpty()) {
                text.append("\nГолы: ");
                text.append(scorers.stream()
                        .map(s -> s.minute() == null || s.minute().isBlank()
                                ? s.scorer()
                                : s.scorer() + " " + s.minute())
                        .collect(Collectors.joining(", ")));
            }
        }
        return text.toString();
    }

    private Integer resolveExistingLiveScoreMessageId(Match match, String primaryKey, Team home, Team away) {
        Integer existing = liveScoreMessageIds.get(primaryKey);
        if (existing != null) {
            return existing;
        }
        if (match.getLiveScoreMessageId() != null) {
            liveScoreMessageIds.put(primaryKey, match.getLiveScoreMessageId());
            return match.getLiveScoreMessageId();
        }
        if (match.getPublicId() != 0) {
            existing = liveScoreMessageIds.get("pub:" + match.getPublicId());
            if (existing != null) {
                liveScoreMessageIds.put(primaryKey, existing);
                return existing;
            }
        }
        if (match.getEspnId() != null && !match.getEspnId().isBlank()) {
            existing = liveScoreMessageIds.get("espn:" + match.getEspnId());
            if (existing != null) {
                liveScoreMessageIds.put(primaryKey, existing);
                return existing;
            }
        }
        if (home != null && away != null) {
            existing = liveScoreMessageIds.get("teams:" + home.getCode() + "-" + away.getCode());
            if (existing != null) {
                liveScoreMessageIds.put(primaryKey, existing);
                return existing;
            }
        }
        return null;
    }

    private void clearLiveScoreState(Match match) {
        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        String primary = buildLiveScoreKey(match, home, away);
        liveScoreMessageIds.remove(primary);
        liveScoreLastTexts.remove(primary);
        if (match.getPublicId() != 0) {
            String publicKey = "pub:" + match.getPublicId();
            liveScoreMessageIds.remove(publicKey);
            liveScoreLastTexts.remove(publicKey);
        }
        if (match.getEspnId() != null && !match.getEspnId().isBlank()) {
            String espnKey = "espn:" + match.getEspnId();
            liveScoreMessageIds.remove(espnKey);
            liveScoreLastTexts.remove(espnKey);
        }
        if (home != null && away != null) {
            String teamsKey = "teams:" + home.getCode() + "-" + away.getCode();
            liveScoreMessageIds.remove(teamsKey);
            liveScoreLastTexts.remove(teamsKey);
        }
    }

    private String buildLiveScoreKey(Match match, Team home, Team away) {
        if (match.getEspnId() != null && !match.getEspnId().isBlank()) {
            return "espn:" + match.getEspnId();
        }
        if (match.getPublicId() != 0) {
            return "pub:" + match.getPublicId();
        }
        String homeCode = home != null ? home.getCode() : String.valueOf(match.getHomeTeamId());
        String awayCode = away != null ? away.getCode() : String.valueOf(match.getAwayTeamId());
        return "teams:" + homeCode + "-" + awayCode;
    }

    public void checkReminders() {
        LocalDateTime now = LocalDateTime.now();
        int horizonMinutes = REMINDER_MINUTES_BEFORE[0] + 1;
        List<Match> upcoming = matchService.findAllNearest(horizonMinutes).stream()
                .filter(m -> Objects.equals(m.getStatus(), "ns"))
                .toList();

        for (Match match : upcoming) {
            long minutesLeft = Duration.between(now, match.getLocalDateTime()).toMinutes();
            for (int reminderMinutes : REMINDER_MINUTES_BEFORE) {
                if (!isReminderWindow(minutesLeft, reminderMinutes)) {
                    continue;
                }
                Map<Integer, List<Lineup>> lineups = api.getLineupsFromEspnSummary(
                        match.getEspnId(),
                        match.getHomeTeamId(),
                        match.getAwayTeamId()
                );
                if (lineups.isEmpty()) {
                    lineups = api.getLineups(match.getPublicId());
                }
                Team homeT = DaoUtil.team(match.getHomeTeamId());
                Team awayT = DaoUtil.team(match.getAwayTeamId());
                List<InjuryInfo> injuries = injuryService.forTeams(
                        homeT != null ? homeT.getCode() : null,
                        awayT != null ? awayT.getCode() : null
                );
                Set<Integer> predictedUserIds = predictionService.getByMatchPublicId(match.getPublicId()).stream()
                        .map(Prediction::getUserId)
                        .collect(Collectors.toSet());
                String imagePathPlain = null;
                String imagePathBonus = null;
                String matchTime = DateTimeFormatter.ofPattern("HH:mm").format(match.getLocalDateTime());
                for (User user : DaoUtil.USERS.values()) {
                    if (predictedUserIds.contains(user.getId())) {
                        continue;
                    }
                    if (!notificationDedupDao.tryMarkReminderSent(user.getId(), match.getPublicId(), reminderMinutes)) {
                        continue;
                    }
                    boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                            user.getId(), match.getWeekId(), match.getPublicId());
                    String imagePath;
                    if (weekBonus) {
                        if (imagePathBonus == null) {
                            imagePathBonus = htmlImages.createReminderImage(
                                    match.getPublicId(),
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
                                    match.getPublicId(),
                                    match.getHomeTeamId(),
                                    match.getAwayTeamId(),
                                    matchTime,
                                    false
                            );
                        }
                        imagePath = imagePathPlain;
                    }
                    Notification notification = Notification.builder()
                            .user(user)
                            .match(match)
                            .lineups(lineups)
                            .injuries(injuries)
                            .build();
                    log.info("Predict reminder: {} min before match, user={}, match={}, weekBonus={}",
                            reminderMinutes, user.getId(), match.getPublicId(), weekBonus);
                    Integer prevMessageId = notificationDedupDao.findLatestReminderTelegramMessageId(
                            user.getId(), match.getPublicId());
                    if (prevMessageId != null && user.getTelegramId() != null && !user.getTelegramId().isBlank()) {
                        api.deleteMessage(user.getTelegramId(), prevMessageId);
                    }
                    Integer messageId = sendNotification(notification, imagePath);
                    notificationDedupDao.saveReminderTelegramMessageId(
                            user.getId(), match.getPublicId(), reminderMinutes, messageId);
                }
            }
        }
    }

    /** Окно для проверки раз в 30 с: напоминание «за N минут» срабатывает при N…N-1 мин до старта. */
    private static boolean isReminderWindow(long minutesLeft, int reminderMinutes) {
        return minutesLeft <= reminderMinutes && minutesLeft > reminderMinutes - 2;
    }

    private Integer sendNotification(Notification notification, String imagePath) {
        log.info("Send notification");
        try {
            Match match = notification.getMatch();
            long minutesLeft = Duration.between(LocalDateTime.now(), match.getLocalDateTime()).toMinutes();
            String chatId = notification.getUser().getTelegramId();

            if (chatId != null && !chatId.isEmpty()) {

                Team home = DaoUtil.team(match.getHomeTeamId());
                Team away = DaoUtil.team(match.getAwayTeamId());
                if (home == null || away == null) {
                    return null;
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

                appendInjuriesCaption(caption, match, notification.getInjuries());

                String text = trimCaption(caption.toString(), 900);
                String replyMarkupJson = objectMapper.writeValueAsString(markup);
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

    private void appendInjuriesCaption(StringBuilder caption, Match match, List<InjuryInfo> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return;
        }
        Team homeTeam = DaoUtil.team(match.getHomeTeamId());
        Team awayTeam = DaoUtil.team(match.getAwayTeamId());
        String homeCode = homeTeam != null ? homeTeam.getCode() : "HOME";
        String awayCode = awayTeam != null ? awayTeam.getCode() : "AWAY";
        List<InjuryInfo> home = injuries.stream()
                .filter(i -> homeCode.equalsIgnoreCase(i.teamCode()))
                .toList();
        List<InjuryInfo> away = injuries.stream()
                .filter(i -> awayCode.equalsIgnoreCase(i.teamCode()))
                .toList();
        if (home.isEmpty() && away.isEmpty()) {
            return;
        }
        caption.append("\nТравмы / отсутствия:\n");
        if (!home.isEmpty()) {
            caption.append(homeCode).append(": ").append(formatInjuryList(home)).append("\n");
        }
        if (!away.isEmpty()) {
            caption.append(awayCode).append(": ").append(formatInjuryList(away)).append("\n");
        }
    }

    private static String formatInjuryList(List<InjuryInfo> injuries) {
        return injuries.stream()
                .map(NotificationService::formatInjuryShort)
                .collect(Collectors.joining("; "));
    }

    private static String formatInjuryShort(InjuryInfo injury) {
        String name = shortPlayerName(injury.playerName());
        String detail = injury.reason() != null && !injury.reason().isBlank()
                ? injury.reason()
                : (injury.status() != null ? injury.status() : "");
        if (detail == null || detail.isBlank()) {
            return name;
        }
        return name + " (" + detail + ")";
    }

    private static String shortPlayerName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String trimmed = fullName.trim();
        if (!trimmed.contains(" ")) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\s+");
        return parts[parts.length - 1];
    }

    private static String trimCaption(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "…";
    }

    private static String padRight(String text, int length) {
        return String.format("%-" + length + "s", text);
    }
}
