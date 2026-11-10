package zhigalin.predictions.service.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import kong.unirest.HttpResponse;
import kong.unirest.MultipartBody;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Player;
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.ResponseTeam;
import zhigalin.predictions.util.TelegramMarkdownV2;

@Service
public class ApiClient {
    @Value("${bot.urlMessage}")
    private String urlMessage;
    @Value("${bot.urlPhoto}")
    private String urlPhoto;
    @Value("${bot.urlAnimation}")
    private String urlAnimation;
    @Value("${bot.urlEditMessage}")
    private String urlEditMessage;
    @Value("${bot.urlEditMessageMedia}")
    private String urlEditMessageMedia;

    private final ObjectMapper mapper;
    private final ApiFootballClient apiFootballClient;
    private final ConcurrentHashMap<Integer, Map<Integer, List<Lineup>>> lineupsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEspnSummary> espnSummaryCache = new ConcurrentHashMap<>();

    private static final String ESPN_SUMMARY_BASE = "https://site.web.api.espn.com/apis/site/v2/sports/soccer/";
    private static final String ESPN_SUMMARY_DEFAULT_LEAGUE = "eng.1";
    private static final long ESPN_SUMMARY_TTL_MS = 8_000L;

    private static final Pattern GOAL_SCORER = Pattern.compile("^Goal!\\s*.+?\\.\\s*(.+?)\\s+\\([^)]+\\)");
    /** Name only; strip ESPN narrative after "with …" / "following …". */
    private static final Pattern GOAL_ASSIST =
            Pattern.compile("Assisted by\\s+(.+?)(?:\\s+(?:with|following)\\b.+?)?\\.");
    /** ESPN own-goal line: "Own Goal by Player, Team. Home 1, Away 0." */
    private static final Pattern OWN_GOAL_SCORER =
            Pattern.compile("^Own Goal by\\s+(.+?),\\s*");
    /** Score after the goal: "Goal! Home 2, Away 1." / "... Team. Home 2, Away 1." */
    private static final Pattern GOAL_SCORE_AFTER =
            Pattern.compile("^Goal!\\s+.+?\\s+(\\d+),\\s+.+?\\s+(\\d+)\\.");
    private static final Pattern OWN_GOAL_SCORE_AFTER =
            Pattern.compile("^Own Goal by\\s+.+?\\.\\s+.+?\\s+(\\d+),\\s+.+?\\s+(\\d+)\\.");

    private static final Logger log = LoggerFactory.getLogger("server");

    public ApiClient(ObjectMapper objectMapper, ApiFootballClient apiFootballClient) {
        this.mapper = objectMapper;
        this.apiFootballClient = apiFootballClient;
    }

    public boolean sendMessage(String chatId, String text, String replyMarkupJson) {
        return sendMessageAndGetId(chatId, text, replyMarkupJson) != null;
    }

    public Integer sendMessageAndGetId(String chatId, String text, String replyMarkupJson) {
        try {
            var req = Unirest.post(urlMessage)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .queryString("chat_id", chatId)
                    .queryString("text", text);
            if (replyMarkupJson != null) {
                req.queryString("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = req.asString();
            if (!checkOk("sendMessage", resp)) {
                return null;
            }
            return extractMessageId(resp.getBody());
        } catch (UnirestException e) {
            log.error("sendMessage error: {}", e.getMessage());
            return null;
        }
    }

    public boolean editMessageText(String chatId, int messageId, String text, String replyMarkupJson) {
        try {
            var req = Unirest.post(urlEditMessage)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .queryString("chat_id", chatId)
                    .queryString("message_id", messageId)
                    .queryString("text", text);
            if (replyMarkupJson != null) {
                req.queryString("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = req.asString();
            return checkOk("editMessageText", resp);
        } catch (UnirestException e) {
            log.error("editMessageText error: {}", e.getMessage());
            return false;
        }
    }

    public Integer sendPhoto(String chatId, String caption, String filePath, String replyMarkupJson) {
        try {
            caption = TelegramMarkdownV2.escape(caption);

            File file = new File(filePath);
            // caption/parse_mode as multipart fields — queryString truncates long lineup captions
            MultipartBody body = Unirest.post(urlPhoto)
                    .header("accept", "application/json")
                    .queryString("chat_id", chatId)
                    .field("photo", file)
                    .field("caption", caption != null ? caption : "")
                    .field("parse_mode", "MarkdownV2");

            if (replyMarkupJson != null) {
                body = body.field("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = body.asString();
            if (!checkOk("sendPhoto", resp)) {
                return null;
            }
            return extractMessageId(resp.getBody());
        } catch (Exception e) {
            log.error("sendPhoto error: {}", e.getMessage());
            return null;
        }
    }

    public Integer sendAnimation(String chatId, String caption, String filePath, String replyMarkupJson) {
        try {
            caption = TelegramMarkdownV2.escape(caption);
            File file = new File(filePath);
            MultipartBody body = Unirest.post(urlAnimation)
                    .header("accept", "application/json")
                    .queryString("chat_id", chatId)
                    .field("animation", file)
                    .field("caption", caption != null ? caption : "")
                    .field("parse_mode", "MarkdownV2");
            if (replyMarkupJson != null) {
                body = body.field("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = body.asString();
            if (!checkOk("sendAnimation", resp)) {
                return null;
            }
            return extractMessageId(resp.getBody());
        } catch (Exception e) {
            log.error("sendAnimation error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * In-place replace of an existing animation (or other media) message.
     * {@code media} uses {@code attach://animation} + multipart file field {@code animation}.
     */
    public boolean editMessageMediaAnimation(
            String chatId, int messageId, String caption, String filePath, String replyMarkupJson
    ) {
        try {
            caption = TelegramMarkdownV2.escape(caption);
            File file = new File(filePath);
            String mediaJson = mapper.writeValueAsString(Map.of(
                    "type", "animation",
                    "media", "attach://animation",
                    "caption", caption != null ? caption : "",
                    "parse_mode", "MarkdownV2"
            ));
            MultipartBody body = Unirest.post(urlEditMessageMedia)
                    .header("accept", "application/json")
                    .queryString("chat_id", chatId)
                    .queryString("message_id", messageId)
                    .field("media", mediaJson)
                    .field("animation", file);
            if (replyMarkupJson != null) {
                body = body.field("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = body.asString();
            return checkOk("editMessageMedia", resp);
        } catch (Exception e) {
            log.error("editMessageMedia error: {}", e.getMessage());
            return false;
        }
    }

    public boolean deleteMessage(String chatId, int messageId) {
        try {
            String urlDelete = urlMessage.replace("sendMessage", "deleteMessage");
            HttpResponse<String> resp = Unirest.post(urlDelete)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .queryString("chat_id", chatId)
                    .queryString("message_id", messageId)
                    .asString();
            return checkOk("deleteMessage", resp);
        } catch (Exception e) {
            log.error("deleteMessage error: chatId={}, messageId={}, err={}", chatId, messageId, e.getMessage());
            return false;
        }
    }

    public Integer sendWidget(
            String chatId,
            String html,
            String chatData,
            Integer height,
            String caption,
            String replyMarkupJson,
            boolean allowQueries
    ) {
        try {
            String url = urlMessage.replace("sendMessage", "sendWidget");
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("html", html);
            if (chatData != null) {
                payload.put("chat_data", chatData);
            }
            if (height != null) {
                payload.put("height", height);
            }
            if (caption != null) {
                payload.put("caption", TelegramMarkdownV2.escape(caption));
                payload.put("parse_mode", "MarkdownV2");
            }
            if (replyMarkupJson != null) {
                payload.put("reply_markup", mapper.readTree(replyMarkupJson));
            }
            if (allowQueries) {
                payload.put("allow_queries", true);
            }
            HttpResponse<String> resp = Unirest.post(url)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .body(mapper.writeValueAsString(payload))
                    .asString();
            if (!isTelegramOk("sendWidget", resp)) {
                return null;
            }
            return extractMessageId(resp.getBody());
        } catch (Exception e) {
            log.error("sendWidget error: {}", e.getMessage());
            return null;
        }
    }

    public boolean answerWidgetQuery(String widgetQueryId, String resultData) {
        try {
            String url = urlMessage.replace("sendMessage", "answerWidgetQuery");
            Map<String, Object> payload = new HashMap<>();
            payload.put("widget_query_id", widgetQueryId);
            if (resultData != null) {
                payload.put("data", resultData);
            }
            HttpResponse<String> resp = Unirest.post(url)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .body(mapper.writeValueAsString(payload))
                    .asString();
            return isTelegramOk("answerWidgetQuery", resp);
        } catch (Exception e) {
            log.error("answerWidgetQuery error: {}", e.getMessage());
            return false;
        }
    }

    public boolean deleteWebhook() {
        try {
            String url = urlMessage.replace("sendMessage", "deleteWebhook");
            HttpResponse<String> resp = Unirest.post(url)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .queryString("drop_pending_updates", false)
                    .asString();
            return isTelegramOk("deleteWebhook", resp);
        } catch (Exception e) {
            log.error("deleteWebhook error: {}", e.getMessage());
            return false;
        }
    }

    public JsonNode getUpdates(long offset, int timeoutSec, List<String> allowedUpdates) {
        try {
            String url = urlMessage.replace("sendMessage", "getUpdates");
            var req = Unirest.get(url)
                    .header("accept", "application/json")
                    .socketTimeout((timeoutSec + 5) * 1000)
                    .queryString("offset", offset)
                    .queryString("timeout", timeoutSec)
                    .queryString("limit", 100);
            if (allowedUpdates != null && !allowedUpdates.isEmpty()) {
                req = req.queryString("allowed_updates", mapper.writeValueAsString(allowedUpdates));
            }
            HttpResponse<String> resp = req.asString();
            if (resp.getBody() == null) {
                return null;
            }
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            log.warn("getUpdates error: {}", e.getMessage());
            return null;
        }
    }

    public boolean editMessageWidgetData(String chatId, int messageId, String chatData) {
        try {
            String url = urlMessage.replace("sendMessage", "editMessageWidgetData");
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            if (chatData != null) {
                payload.put("chat_data", chatData);
            }
            HttpResponse<String> resp = Unirest.post(url)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .body(mapper.writeValueAsString(payload))
                    .asString();
            String body = resp.getBody();
            if (body != null && body.contains("message is not modified")) {
                log.info("editMessageWidgetData: not modified (ok)");
                return true;
            }
            return isTelegramOk("editMessageWidgetData", resp);
        } catch (Exception e) {
            log.error("editMessageWidgetData error: chatId={}, messageId={}, err={}",
                    chatId, messageId, e.getMessage());
            return false;
        }
    }

    private boolean checkOk(String op, HttpResponse<String> resp) {
        if (resp.getStatus() == 200) {
            log.info("{}: ok", op);
            return true;
        } else {
            log.warn("{}: status={} body={}", op, resp.getStatus(), resp.getBody());
            return false;
        }
    }

    private boolean isTelegramOk(String op, HttpResponse<String> resp) {
        String body = resp.getBody();
        try {
            if (body != null) {
                JsonNode root = mapper.readTree(body);
                if (root.path("ok").asBoolean(false)) {
                    log.info("{}: ok", op);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        log.warn("{}: status={} body={}", op, resp.getStatus(), body);
        return false;
    }

    private Integer extractMessageId(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!root.path("ok").asBoolean(false)) {
                return null;
            }
            JsonNode messageIdNode = root.path("result").path("message_id");
            return messageIdNode.isInt() ? messageIdNode.asInt() : null;
        } catch (Exception e) {
            log.warn("extractMessageId parse error: {}", e.getMessage());
            return null;
        }
    }

    public Map<Integer, List<Lineup>> getLineups(int publicId) {
        Map<Integer, List<Lineup>> cached = lineupsCache.get(publicId);
        if (cached != null) {
            return cached;
        }
        try {
            Map<Integer, List<Lineup>> lineups = new HashMap<>();
            for (Response response : apiFootballClient.fetchFixtureLineups(publicId)) {
                ResponseTeam team = response.getTeam();
                if (team == null) {
                    continue;
                }
                int teamId = team.getId();
                List<Lineup> lineup = response.getLineup();
                lineups.put(teamId, lineup == null ? List.of() : List.copyOf(lineup));
            }
            if (!lineups.isEmpty()) {
                Map<Integer, List<Lineup>> frozen = Map.copyOf(lineups);
                lineupsCache.put(publicId, frozen);
                return frozen;
            }
            return lineups;
        } catch (Exception e) {
            log.error("Lineups  error: {}", e.getMessage());
            return Map.of();
        }
    }

    public record EspnLineups(
            Map<Integer, List<Lineup>> byTeam,
            String homeFormation,
            String awayFormation
    ) {
        public static EspnLineups empty() {
            return new EspnLineups(Map.of(), null, null);
        }

        public boolean isEmpty() {
            return byTeam == null || byTeam.isEmpty();
        }
    }

    public Map<Integer, List<Lineup>> getLineupsFromEspnSummary(String espnEventId, int homeTeamId, int awayTeamId) {
        return getEspnLineups(espnEventId, homeTeamId, awayTeamId).byTeam();
    }

    public EspnLineups getEspnLineups(String espnEventId, int homeTeamId, int awayTeamId) {
        if (espnEventId == null || espnEventId.isBlank()) {
            return EspnLineups.empty();
        }
        try {
            JsonNode root = fetchEspnSummary(espnEventId);
            if (root == null) {
                return EspnLineups.empty();
            }
            JsonNode rosters = root.path("rosters");
            if (!rosters.isArray() || rosters.isEmpty()) {
                return EspnLineups.empty();
            }
            Map<Integer, List<Lineup>> result = new HashMap<>();
            String homeFormation = null;
            String awayFormation = null;
            for (JsonNode rosterNode : rosters) {
                String homeAway = rosterNode.path("homeAway").asText("");
                int teamId = "home".equalsIgnoreCase(homeAway) ? homeTeamId : "away".equalsIgnoreCase(homeAway) ? awayTeamId : 0;
                if (teamId == 0) {
                    continue;
                }
                String formation = rosterNode.path("formation").asText("").trim();
                if (!formation.isEmpty()) {
                    if ("home".equalsIgnoreCase(homeAway)) {
                        homeFormation = formation;
                    } else {
                        awayFormation = formation;
                    }
                }
                JsonNode roster = rosterNode.path("roster");
                if (!roster.isArray() || roster.isEmpty()) {
                    continue;
                }
                List<Lineup> starters = new ArrayList<>();
                for (JsonNode athleteNode : roster) {
                    if (!athleteNode.path("starter").asBoolean(false)) {
                        continue;
                    }
                    String name = athleteNode.path("athlete").path("displayName").asText("").trim();
                    if (name.isBlank()) {
                        continue;
                    }
                    Player player = new Player();
                    player.setName(name);
                    player.setNumber(athleteNode.path("jersey").asInt(0));
                    player.setPos(athleteNode.path("position").path("abbreviation").asText("").trim());
                    player.setFormationPlace(athleteNode.path("formationPlace").asInt(0));
                    Lineup lineup = new Lineup();
                    lineup.setPlayer(player);
                    starters.add(lineup);
                }
                if (!starters.isEmpty()) {
                    result.put(teamId, List.copyOf(starters));
                }
            }
            if (result.isEmpty()) {
                return EspnLineups.empty();
            }
            return new EspnLineups(Map.copyOf(result), homeFormation, awayFormation);
        } catch (Exception e) {
            log.warn("ESPN lineups error: {}", e.getMessage());
            return EspnLineups.empty();
        }
    }

    public void evictLineups(int publicId) {
        lineupsCache.remove(publicId);
    }

    /**
     * Fetches ESPN match summary JSON with a short in-memory TTL.
     * Failed/empty responses are not cached so retries are not stuck.
     */
    public JsonNode fetchEspnSummary(String espnEventId) {
        return fetchEspnSummary(espnEventId, ESPN_SUMMARY_DEFAULT_LEAGUE);
    }

    public JsonNode fetchEspnSummary(String espnEventId, String leagueSlug) {
        if (espnEventId == null || espnEventId.isBlank()) {
            return null;
        }
        String league = (leagueSlug == null || leagueSlug.isBlank()) ? ESPN_SUMMARY_DEFAULT_LEAGUE : leagueSlug.trim();
        String cacheKey = league + "|" + espnEventId;
        long now = System.currentTimeMillis();
        CachedEspnSummary cached = espnSummaryCache.get(cacheKey);
        if (cached != null && now - cached.fetchedAtMs() < ESPN_SUMMARY_TTL_MS) {
            return cached.root();
        }
        try {
            String url = ESPN_SUMMARY_BASE + league + "/summary";
            HttpResponse<String> response = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "predictions-bot/1.0")
                    .queryString("event", espnEventId)
                    .asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                espnSummaryCache.remove(cacheKey);
                return null;
            }
            JsonNode root = mapper.readTree(response.getBody());
            if (root == null || root.isMissingNode() || root.isNull()) {
                espnSummaryCache.remove(cacheKey);
                return null;
            }
            espnSummaryCache.put(cacheKey, new CachedEspnSummary(root, now));
            return root;
        } catch (Exception e) {
            espnSummaryCache.remove(cacheKey);
            log.warn("ESPN summary fetch failed: league={}, espnEventId={}, error={}",
                    league, espnEventId, e.getMessage());
            return null;
        }
    }

    /**
     * Standing goal scorers from ESPN commentary (no assists).
     * Includes own goals ({@code Own Goal by …} → {@code Name (авт.)}).
     * If commentary has more Goal!/OG lines than {@code expectedTotal} (VAR overturn),
     * keeps the earliest {@code expectedTotal} goals.
     */
    public List<GoalScorer> listGoalScorers(String espnEventId, int expectedTotal) {
        if (espnEventId == null || espnEventId.isBlank() || expectedTotal <= 0) {
            return List.of();
        }
        try {
            JsonNode root = fetchEspnSummary(espnEventId);
            if (root == null) {
                return List.of();
            }
            return extractGoalScorers(root, expectedTotal);
        } catch (Exception e) {
            log.warn("ESPN goal scorers lookup failed: espnEventId={}, error={}", espnEventId, e.getMessage());
            return List.of();
        }
    }

    List<GoalScorer> extractGoalScorers(JsonNode root, int expectedTotal) {
        JsonNode commentary = root.path("commentary");
        if (!commentary.isArray() || commentary.isEmpty() || expectedTotal <= 0) {
            return List.of();
        }
        List<GoalCommentaryEntry> goals = new ArrayList<>();
        for (int i = 0; i < commentary.size(); i++) {
            JsonNode item = commentary.get(i);
            String text = item.path("text").asText("").trim();
            LatestGoalInfo parsed = parseGoalCommentary(text);
            if (parsed == null || parsed.scorer() == null || parsed.scorer().isBlank()) {
                continue;
            }
            double timeValue = item.path("time").path("value").asDouble(-1d);
            long sequence = item.path("sequence").asLong(i);
            String minute = formatGoalMinute(item.path("time"));
            goals.add(new GoalCommentaryEntry(timeValue, sequence, parsed, minute));
        }
        if (goals.isEmpty()) {
            return List.of();
        }
        goals.sort(Comparator
                .comparingDouble(GoalCommentaryEntry::timeValue)
                .thenComparingLong(GoalCommentaryEntry::sequence));
        int limit = Math.min(expectedTotal, goals.size());
        List<GoalScorer> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            GoalCommentaryEntry entry = goals.get(i);
            LatestGoalInfo info = entry.info();
            result.add(new GoalScorer(
                    info.scorer(),
                    entry.minute(),
                    info.homeScore(),
                    info.awayScore()
            ));
        }
        return result;
    }

    private static String formatGoalMinute(JsonNode timeNode) {
        if (timeNode == null || timeNode.isMissingNode()) {
            return null;
        }
        String display = timeNode.path("displayValue").asText("").trim();
        if (!display.isBlank()) {
            return display.endsWith("'") ? display : display + "'";
        }
        if (timeNode.has("value") && !timeNode.path("value").isNull()) {
            int value = timeNode.path("value").asInt(-1);
            if (value >= 0) {
                return value + "'";
            }
        }
        return null;
    }

    LatestGoalInfo parseGoalCommentary(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher ownGoalMatcher = OWN_GOAL_SCORER.matcher(text);
        if (ownGoalMatcher.find()) {
            String scorer = ownGoalMatcher.group(1).trim();
            if (scorer.isEmpty()) {
                return null;
            }
            int[] score = parseScoreAfter(text, OWN_GOAL_SCORE_AFTER);
            return new LatestGoalInfo(
                    scorer + " (авт.)",
                    null,
                    score != null ? score[0] : null,
                    score != null ? score[1] : null
            );
        }
        if (!text.startsWith("Goal!")) {
            return null;
        }
        Matcher scorerMatcher = GOAL_SCORER.matcher(text);
        if (!scorerMatcher.find()) {
            return null;
        }
        String scorer = scorerMatcher.group(1).trim();
        String assist = null;
        Matcher assistMatcher = GOAL_ASSIST.matcher(text);
        if (assistMatcher.find()) {
            assist = assistMatcher.group(1).trim();
        }
        int[] score = parseScoreAfter(text, GOAL_SCORE_AFTER);
        return new LatestGoalInfo(
                scorer,
                assist,
                score != null ? score[0] : null,
                score != null ? score[1] : null
        );
    }

    private static int[] parseScoreAfter(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    private record CachedEspnSummary(JsonNode root, long fetchedAtMs) {
    }

    private record GoalCommentaryEntry(double timeValue, long sequence, LatestGoalInfo info, String minute) {
    }

    public record LatestGoalInfo(String scorer, String assist, Integer homeScore, Integer awayScore) {
    }

    public record GoalScorer(String scorer, String minute, Integer homeScore, Integer awayScore) {
        /** e.g. {@code 2:1 Haaland 74'} or {@code Salah 23'} if score unknown. */
        public String formatForCaption() {
            StringBuilder sb = new StringBuilder();
            if (homeScore != null && awayScore != null) {
                sb.append(homeScore).append(':').append(awayScore).append(' ');
            }
            sb.append(scorer != null ? scorer : "");
            if (minute != null && !minute.isBlank()) {
                sb.append(' ').append(minute);
            }
            return sb.toString().trim();
        }
    }
}
