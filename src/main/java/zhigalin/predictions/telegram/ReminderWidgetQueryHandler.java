package zhigalin.predictions.telegram;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.notification.ReminderWidgetFactory;
import zhigalin.predictions.service.notification.ReminderWidgetHtml;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

@Service
public class ReminderWidgetQueryHandler {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final Pattern SAVE = Pattern.compile("^s:(\\d+):(\\d+):(\\d+)$");
    private static final DateTimeFormatter KICK = DateTimeFormatter.ofPattern("HH:mm");

    private final ApiClient api;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final UserService userService;
    private final ReminderWidgetFactory reminderWidgetFactory;

    public ReminderWidgetQueryHandler(
            ApiClient api,
            MatchService matchService,
            PredictionService predictionService,
            UserService userService,
            ReminderWidgetFactory reminderWidgetFactory
    ) {
        this.api = api;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.userService = userService;
        this.reminderWidgetFactory = reminderWidgetFactory;
    }

    public void handle(JsonNode widgetQuery) {
        if (widgetQuery == null || widgetQuery.isMissingNode()) {
            return;
        }
        String queryId = text(widgetQuery, "id");
        if (queryId == null || queryId.isBlank()) {
            return;
        }
        boolean answered = false;
        try {
            String data = extractData(widgetQuery);
            Matcher m = data == null ? null : SAVE.matcher(data);
            if (m == null || !m.matches()) {
                api.answerWidgetQuery(queryId, "bad");
                return;
            }
            int matchId = Integer.parseInt(m.group(1));
            int homeScore = Integer.parseInt(m.group(2));
            int awayScore = Integer.parseInt(m.group(3));
            if (homeScore < 0 || homeScore > ReminderWidgetHtml.SCORE_MAX
                    || awayScore < 0 || awayScore > ReminderWidgetHtml.SCORE_MAX) {
                api.answerWidgetQuery(queryId, "score");
                return;
            }

            long fromId = widgetQuery.path("from").path("id").asLong(0L);
            if (fromId <= 0) {
                api.answerWidgetQuery(queryId, "user");
                return;
            }
            User user = userService.findByTelegramId(String.valueOf(fromId));
            if (user == null) {
                api.answerWidgetQuery(queryId, "user");
                return;
            }

            Match match = matchService.findById(matchId);
            if (match == null) {
                api.answerWidgetQuery(queryId, "match");
                return;
            }
            if (match.getStartsAt() != null
                    && LocalDateTime.now().isAfter(match.getStartsAt().plusMinutes(5))) {
                api.answerWidgetQuery(queryId, "late");
                return;
            }

            String chatId = chatId(widgetQuery);
            Integer messageId = messageId(widgetQuery);
            if (chatId == null || messageId == null) {
                api.answerWidgetQuery(queryId, "msg");
                return;
            }

            predictionService.save(Prediction.builder()
                    .userId(user.getId())
                    .matchId(matchId)
                    .homeScore(homeScore)
                    .awayScore(awayScore)
                    .points(null)
                    .build());
            Prediction saved = predictionService.findByMatchIdAndUserId(matchId, user.getId());
            if (saved == null
                    || saved.getHomeScore() == null
                    || saved.getAwayScore() == null
                    || saved.getHomeScore() != homeScore
                    || saved.getAwayScore() != awayScore) {
                api.answerWidgetQuery(queryId, "save");
                return;
            }

            api.answerWidgetQuery(queryId, "ok");
            answered = true;
            long minutesLeft = match.getStartsAt() == null
                    ? 0
                    : Math.max(0, java.time.Duration.between(LocalDateTime.now(), match.getStartsAt()).toMinutes());
            String kick = match.getStartsAt() == null ? "--:--" : KICK.format(match.getStartsAt());
            String predicted = homeScore + ":" + awayScore;
            ApiClient.EspnLineups espnLineups = api.getEspnLineups(
                    match.getEspnId(), match.getHomeTeamId(), match.getAwayTeamId());
            Map<Integer, List<Lineup>> lineups = espnLineups.byTeam();
            if (!ReminderWidgetFactory.hasPlayers(lineups)) {
                lineups = api.getLineups(match.getId());
            }
            ReminderWidgetFactory.LineupSnapshot resolved = ReminderWidgetFactory.resolveLineups(
                    lineups,
                    espnLineups.homeFormation(),
                    espnLineups.awayFormation(),
                    ReminderWidgetFactory.lastLineups(chatId, messageId));
            lineups = resolved.byTeam();
            String homeFormation = resolved.homeFormation();
            String awayFormation = resolved.awayFormation();
            ReminderWidgetFactory.Payload payload = reminderWidgetFactory.build(
                    match, user.getId(), minutesLeft, kick, predicted, lineups,
                    homeFormation, awayFormation);
            ReminderWidgetFactory.rememberLineups(
                    chatId, messageId, lineups, homeFormation, awayFormation);
            if (!api.editMessageWidgetData(chatId, messageId, payload.chatData())) {
                log.warn("widget save ok but editMessageWidgetData failed chatId={} messageId={}",
                        chatId, messageId);
            }
        } catch (Exception e) {
            log.error("widget_query handle error: {}", e.getMessage(), e);
            if (!answered) {
                api.answerWidgetQuery(queryId, "err");
            }
        }
    }

    private static String extractData(JsonNode widgetQuery) {
        if (widgetQuery.hasNonNull("data")) {
            return widgetQuery.get("data").asText();
        }
        JsonNode type = widgetQuery.path("type");
        if (type.hasNonNull("data")) {
            return type.get("data").asText();
        }
        return null;
    }

    private static String chatId(JsonNode widgetQuery) {
        JsonNode chat = widgetQuery.path("chat");
        if (chat.has("id")) {
            return chat.get("id").asText();
        }
        JsonNode message = widgetQuery.path("message");
        if (message.path("chat").has("id")) {
            return message.path("chat").get("id").asText();
        }
        return null;
    }

    private static Integer messageId(JsonNode widgetQuery) {
        if (widgetQuery.has("message_id") && !widgetQuery.get("message_id").isNull()) {
            return widgetQuery.get("message_id").asInt();
        }
        JsonNode message = widgetQuery.path("message");
        if (message.has("message_id") && !message.get("message_id").isNull()) {
            return message.get("message_id").asInt();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
