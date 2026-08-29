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
import zhigalin.predictions.model.input.Root;
import zhigalin.predictions.util.TelegramMarkdownV2;

@Service
public class ApiClient {
    @Value("${api.football.token}")
    private String apiFootballToken;
    @Value("${bot.urlMessage}")
    private String urlMessage;
    @Value("${bot.urlPhoto}")
    private String urlPhoto;
    @Value("${bot.urlEditMessage}")
    private String urlEditMessage;

    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Integer, Map<Integer, List<Lineup>>> lineupsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEspnSummary> espnSummaryCache = new ConcurrentHashMap<>();

    private static final String X_RAPIDAPI_KEY = "x-rapidapi-key";
    private static final String HOST_NAME = "x-rapidapi-host";
    private static final String HOST = "v3.football.api-sports.io";
    private static final String BASE_URL = "https://v3.football.api-sports.io/fixtures/";
    private static final String LINEUPS = "lineups";
    private static final String ESPN_SUMMARY_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/summary";
    private static final long ESPN_SUMMARY_TTL_MS = 8_000L;

    private static final Pattern GOAL_SCORER = Pattern.compile("^Goal!\\s*.+?\\.\\s*(.+?)\\s+\\([^)]+\\)");
    private static final Pattern GOAL_ASSIST = Pattern.compile("Assisted by\\s+(.+?)(?:\\s+with\\s+.+?)?\\.");

    private static final Logger log = LoggerFactory.getLogger("server");

    public ApiClient(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
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

    public void sendPhoto(String chatId, String caption, String filePath, String replyMarkupJson) {
        try {
            caption = TelegramMarkdownV2.escape(caption);

            File file = new File(filePath);
            MultipartBody body = Unirest.post(urlPhoto)
                    .header("accept", "application/json")
                    .queryString("chat_id", chatId)
                    .queryString("caption", caption)
                    .queryString("parse_mode", "MarkdownV2")
                    .field("photo", file);

            if (replyMarkupJson != null) {
                body = body.queryString("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = body.asString();
            checkOk("sendPhoto", resp);
        } catch (Exception e) {
            log.error("sendPhoto error: {}", e.getMessage());
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
            HttpResponse<String> resp = Unirest.get(BASE_URL + LINEUPS)
                    .header(X_RAPIDAPI_KEY, apiFootballToken)
                    .header(HOST_NAME, HOST)
                    .queryString("fixture", publicId)
                    .queryString("type", "startXI")
                    .asString();
            Root root = mapper.readValue(resp.getBody(), Root.class);

            Map<Integer, List<Lineup>> lineups = new HashMap<>();
            for (Response response : root.getResponse()) {
                ResponseTeam team = response.getTeam();
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

    public Map<Integer, List<Lineup>> getLineupsFromEspnSummary(String espnEventId, int homeTeamId, int awayTeamId) {
        if (espnEventId == null || espnEventId.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = fetchEspnSummary(espnEventId);
            if (root == null) {
                return Map.of();
            }
            JsonNode rosters = root.path("rosters");
            if (!rosters.isArray() || rosters.isEmpty()) {
                return Map.of();
            }
            Map<Integer, List<Lineup>> result = new HashMap<>();
            for (JsonNode rosterNode : rosters) {
                String homeAway = rosterNode.path("homeAway").asText("");
                int teamId = "home".equalsIgnoreCase(homeAway) ? homeTeamId : "away".equalsIgnoreCase(homeAway) ? awayTeamId : 0;
                if (teamId == 0) {
                    continue;
                }
                JsonNode roster = rosterNode.path("roster");
                if (!roster.isArray() || roster.isEmpty()) {
                    continue;
                }
                List<Lineup> starters = new java.util.ArrayList<>();
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
                    Lineup lineup = new Lineup();
                    lineup.setPlayer(player);
                    starters.add(lineup);
                }
                if (!starters.isEmpty()) {
                    result.put(teamId, List.copyOf(starters));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("ESPN lineups error: {}", e.getMessage());
            return Map.of();
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
        if (espnEventId == null || espnEventId.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedEspnSummary cached = espnSummaryCache.get(espnEventId);
        if (cached != null && now - cached.fetchedAtMs() < ESPN_SUMMARY_TTL_MS) {
            return cached.root();
        }
        try {
            HttpResponse<String> response = Unirest.get(ESPN_SUMMARY_URL)
                    .queryString("event", espnEventId)
                    .asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                espnSummaryCache.remove(espnEventId);
                return null;
            }
            JsonNode root = mapper.readTree(response.getBody());
            if (root == null || root.isMissingNode() || root.isNull()) {
                espnSummaryCache.remove(espnEventId);
                return null;
            }
            espnSummaryCache.put(espnEventId, new CachedEspnSummary(root, now));
            return root;
        } catch (Exception e) {
            espnSummaryCache.remove(espnEventId);
            log.warn("ESPN summary fetch failed: espnEventId={}, error={}", espnEventId, e.getMessage());
            return null;
        }
    }

    public LatestGoalInfo findLatestGoalInfo(String espnEventId) {
        if (espnEventId == null || espnEventId.isBlank()) {
            return null;
        }
        try {
            JsonNode root = fetchEspnSummary(espnEventId);
            if (root == null) {
                return null;
            }
            JsonNode commentary = root.path("commentary");
            if (!commentary.isArray() || commentary.isEmpty()) {
                return null;
            }
            List<GoalCommentaryEntry> goals = new ArrayList<>();
            for (int i = 0; i < commentary.size(); i++) {
                JsonNode item = commentary.get(i);
                String text = item.path("text").asText("").trim();
                if (!text.startsWith("Goal!")) {
                    continue;
                }
                LatestGoalInfo parsed = parseGoalCommentary(text);
                if (parsed == null || parsed.scorer() == null || parsed.scorer().isBlank()) {
                    continue;
                }
                double timeValue = item.path("time").path("value").asDouble(-1d);
                long sequence = item.path("sequence").asLong(i);
                goals.add(new GoalCommentaryEntry(timeValue, sequence, parsed));
            }
            if (goals.isEmpty()) {
                return null;
            }
            goals.sort(Comparator
                    .comparingDouble(GoalCommentaryEntry::timeValue)
                    .thenComparingLong(GoalCommentaryEntry::sequence)
                    .reversed());
            return goals.getFirst().info();
        } catch (Exception e) {
            log.warn("ESPN latest goal lookup failed: espnEventId={}, error={}", espnEventId, e.getMessage());
            return null;
        }
    }

    private LatestGoalInfo parseGoalCommentary(String text) {
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
        return new LatestGoalInfo(scorer, assist);
    }

    private record CachedEspnSummary(JsonNode root, long fetchedAtMs) {
    }

    private record GoalCommentaryEntry(double timeValue, long sequence, LatestGoalInfo info) {
    }

    public record LatestGoalInfo(String scorer, String assist) {
    }
}
