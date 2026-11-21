package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
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
            body.put("temperature", 0.45);
            body.put("max_tokens", 700);
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
            return new Result(trimToThreeParagraphs(content.asText()), false);
        } catch (Exception e) {
            log.warn("Groq narrative failed: {}", e.getMessage());
            return new Result(null, false);
        }
    }

    static String trimToThreeParagraphs(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String[] parts = raw.trim().split("\\n\\s*\\n+");
        List<String> paras = new ArrayList<>();
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) {
                paras.add(p.replaceAll("[ \\t]+", " "));
            }
            if (paras.size() == 3) {
                break;
            }
        }
        if (paras.isEmpty()) {
            return raw.trim();
        }
        if (paras.size() == 1) {
            return keepTail(paras.get(0), 1_400);
        }
        String joined = String.join("\n\n", paras);
        if (joined.length() <= 1_400 || paras.size() < 3) {
            return joined.length() <= 1_400 ? joined : keepTail(joined, 1_400);
        }
        String close = paras.get(2);
        String intro = paras.get(0);
        int budget = 1_400 - intro.length() - close.length() - 4;
        if (budget < 40) {
            return intro + "\n\n" + close;
        }
        String form = paras.get(1);
        if (form.length() > budget) {
            form = shortenAtSentence(form, budget);
        }
        return intro + "\n\n" + form + "\n\n" + close;
    }

    private static String keepTail(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        String tail = text.substring(text.length() - maxLen);
        int first = indexOfSentenceStart(tail);
        if (first > 0 && first < tail.length() - 40) {
            return tail.substring(first).trim();
        }
        int sp = tail.indexOf(' ');
        return (sp > 0 && sp < tail.length() - 20 ? tail.substring(sp + 1) : tail).trim();
    }

    private static int indexOfSentenceStart(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && text.charAt(i + 1) == ' ') {
                return i + 2;
            }
        }
        return -1;
    }

    private static String shortenAtSentence(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        String cut = text.substring(0, maxLen);
        int last = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
        if (last >= 40) {
            return cut.substring(0, last + 1).trim();
        }
        int sp = cut.lastIndexOf(' ');
        return (sp > 20 ? cut.substring(0, sp) : cut).trim();
    }

    static final String SYSTEM_PROMPT =
            "Вход — компактный протокол матча (не JSON). Легенда:\n"
                    + "M|homeName|homeCode|home|awayName|awayCode|away\n"
                    + "TIP|h:a|… — целевой счёт прогноза (ориентир для заключения)\n"
                    + "MS|h:a|… — запасной ориентир, если TIP нет\n"
                    + "SS.HA / SS.OV / T.* — форма и превью; o25%=доля матчей с тоталом >2.5 "
                    + "(не минуты); f8=форма last8; late%=голы на 76–90\n"
                    + "Ровно 3 абзаца на русском, без заголовков и markdown, "
                    + "между абзацами пустая строка:\n"
                    + "1) Вводная — короткое лирическое описание предстоящего матча "
                    + "(атмосфера, интрига, характер встречи), без цифр и без прогноза.\n"
                    + "2) Форма — небольшой текстовый обзор текущей формы хозяев и гостей "
                    + "по данным входа; 2–4 ключевые цифры максимум, внутри фраз; "
                    + "ppg%/XX% в SS — доля набранных очков, не процент побед; "
                    + "не перечисляй все поля протокола.\n"
                    + "3) Заключение — прогноз: назови вероятный счёт из TIP (иначе MS) "
                    + "и одной фразой почему; без слов tip/λ/model/1X2/протокол.\n"
                    + "Коды команд — полными названиями. "
                    + "Не выдумывай травмы, составы, авторов голов, погоду, стадионы "
                    + "и точные счёты прошлых игр, если их нет во входе. "
                    + "Не путай gf с ga.";

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
