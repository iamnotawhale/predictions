package zhigalin.predictions.service.api;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.ResponseTeam;
import zhigalin.predictions.model.input.Root;

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

    private static final String X_RAPIDAPI_KEY = "x-rapidapi-key";
    private static final String HOST_NAME = "x-rapidapi-host";
    private static final String HOST = "v3.football.api-sports.io";
    private static final String BASE_URL = "https://v3.football.api-sports.io/fixtures/";
    private static final String LINEUPS = "lineups";

    private static final List<String> escaped = List.of("_", "*", "[", "]", "(", ")", "~", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!");

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
            for (String s : escaped) {
                if (caption.contains(s)) {
                    caption = caption.replace(s, "\\" + s);
                }
            }

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

                lineups.put(teamId, lineup);
            }
            return lineups;
        } catch (Exception e) {
            log.error("Lineups  error: {}", e.getMessage());
            return Map.of();
        }
    }
}
