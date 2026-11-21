package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GroqNarrativeClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Result(String text, boolean rateLimited) {
    }

    public boolean isConfigured() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    public Result generate(String prompt) {
        String key = apiKey();
        if (key == null || key.isBlank()) {
            return new Result(null, false);
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", modelId());
            body.put("temperature", 0.55);
            body.put("max_tokens", 2200);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", SYSTEM_PROMPT);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", prompt);

            HttpResponse<String> response = Unirest.post(URL)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .connectTimeout(12_000)
                    .socketTimeout(45_000)
                    .body(MAPPER.writeValueAsString(body))
                    .asString();

            if (response.getStatus() == 429) {
                log.warn("Groq rate limited (429), stopping remaining narratives");
                return new Result(null, true);
            }
            if (!response.isSuccess() || response.getBody() == null) {
                log.warn("Groq request failed: status {}", response.getStatus());
                return new Result(null, false);
            }
            JsonNode root = MAPPER.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return new Result(null, false);
            }
            String text = content.asText().trim();
            if (text.length() > 3_200) {
                text = text.substring(0, 3_200);
            }
            return new Result(text, false);
        } catch (Exception e) {
            log.warn("Groq narrative failed: {}", e.getMessage());
            return new Result(null, false);
        }
    }

    static final String SYSTEM_PROMPT =
            "Вход — компактный протокол матча (не JSON). Легенда:\n"
                    + "M|homeName|homeCode|home|awayName|awayCode|away\n"
                    + "TIP|h:a|p=scoreProb|lam=λh/λa  (внутренний ориентир, не цитировать как tip/λ)\n"
                    + "MS|h:a|p=modelProb|1x2=h/d/a  (внутренний ориентир, не цитировать)\n"
                    + "SS.HA|code|scope|cell,cell,…  Statistical Preview home/away; "
                    + "обычно ppg%,gf,ga,tg,o25 (o25=доля матчей с тоталом >2.5, НЕ минуты)\n"
                    + "SS.OV|code|scope|…  overall preview, те же поля\n"
                    + "T.code|s=ov/h/a|c=ov/h/a|xg=xg/xga|sf%=scored-first%|cf%=conceded-first%|"
                    + "lead%|trail%|eqS%|eqC%|ogs%|ogc%|avg1st|favPpg|late%=доля голов 76-90|"
                    + "lateGA%|2hGF%=доля своих голов во 2 тайме|avgGF|avgGA|"
                    + "f8=ppg/gf/ga[/oppPpg] форма last8|htPpg|winHt%|2hPpg|hPpg|aPpg|"
                    + "btts%|cs%|o25%|draw%|rxg=recentXg/xga[/n]|xPts|xPtsD\n"
                    + "Пиши текстовый Statistical Preview на русском как на SoccerSTATS: "
                    + "газетный pre-match, 4–6 абзацев. Структура: зацепка; хозяева; гости; "
                    + "тенденции матча; мягкий финал без ставки и без слов tip/λ/model/1X2/протокол. "
                    + "Цифры только внутри фраз. Не печатай вход и markdown. "
                    + "ppg% — доля набранных очков. Коды — полными названиями. "
                    + "Не выдумывай травмы, составы, авторов голов и точные счёты прошлых игр, "
                    + "если их нет во входе.";

    private static String apiKey() {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("GROQ_API_KEY");
        }
        return key;
    }

    private static String modelId() {
        String model = System.getenv("GROQ_MODEL");
        if (model == null || model.isBlank()) {
            model = System.getProperty("GROQ_MODEL");
        }
        if (model == null || model.isBlank()) {
            return "openai/gpt-oss-20b";
        }
        return model.trim();
    }
}
