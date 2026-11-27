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

    private static final int RATE_LIMIT_RETRIES = 4;
    private static final long RATE_LIMIT_BACKOFF_MS = 70_000L;

    public Result generate(String prompt) {
        Result first = requestWithBackoff(prompt, 0.35);
        if (first.rateLimited()) {
            return first;
        }
        String cleaned = cleanNarrative(first.text());
        if (cleaned != null) {
            return new Result(cleaned, false);
        }
        Result second = requestWithBackoff(prompt + "\n\n" + CJK_RETRY_SUFFIX, 0.2);
        if (second.rateLimited()) {
            return second;
        }
        cleaned = cleanNarrative(second.text());
        return new Result(cleaned, false);
    }

    private Result requestWithBackoff(String prompt, double temperature) {
        for (int attempt = 1; attempt <= RATE_LIMIT_RETRIES; attempt++) {
            Result result = requestOnce(prompt, temperature);
            if (!result.rateLimited()) {
                return result;
            }
            if (attempt == RATE_LIMIT_RETRIES) {
                log.warn("Groq rate limited (429) after {} attempts", RATE_LIMIT_RETRIES);
                return result;
            }
            log.warn("Groq rate limited (429), backing off {} ms (attempt {}/{})",
                    RATE_LIMIT_BACKOFF_MS, attempt, RATE_LIMIT_RETRIES);
            sleep(RATE_LIMIT_BACKOFF_MS);
        }
        return new Result(null, true);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Result requestOnce(String prompt, double temperature) {
        String key = apiKey();
        if (key == null || key.isBlank()) {
            return new Result(null, false);
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", modelId());
            body.put("temperature", temperature);
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
            return new Result(content.asText(), false);
        } catch (Exception e) {
            log.warn("Groq narrative failed: {}", e.getMessage());
            return new Result(null, false);
        }
    }

    static String cleanNarrative(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace('\u00A0', ' ').trim();
        if (hasCjk(normalized)) {
            normalized = stripCjk(normalized);
        }
        if (normalized.isBlank()) {
            return null;
        }
        String trimmed = trimToThreeParagraphs(normalized);
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }
        if (hasCjk(trimmed)) {
            trimmed = stripCjk(trimmed);
        }
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    static boolean hasCjk(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isCjk(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    static String stripCjk(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!isCjk(cp)) {
                sb.appendCodePoint(cp);
            } else {
                sb.append(' ');
            }
            i += Character.charCount(cp);
        }
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || (cp >= 0x3000 && cp <= 0x303F);
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

    static final String CJK_RETRY_SUFFIX =
            "Перепиши строго по-русски: без иероглифов, без латиницы "
                    + "вместо русских слов; цифры хозяев только из строк FACTS «дома», "
                    + "гостей — только из строк FACTS «в гостях»; числа не меняй и не добавляй; "
                    + "абзацы разделяй пустой строкой; в третьем абзаце назови счёт TIP/MS "
                    + "и можно опереться на цифры из FACTS.";

    static final String SYSTEM_PROMPT =
            "Вход — компактный протокол матча (не JSON).\n"
                    + "M|homeName|homeCode|home|awayName|awayCode|away\n"
                    + "TIP|h:a|… — целевой счёт; MS|h:a|… — запасной\n"
                    + "T.code|form=overall|… — общая форма (только для словесного впечатления, "
                    + "цифры оттуда не цитировать)\n"
                    + "FACTS — готовые русские строки с цифрами venue "
                    + "(xG / очков за матч / голов за матч дома или в гостях)\n"
                    + "Ровно 3 абзаца на русском, без заголовков и markdown, пустая строка между ними:\n"
                    + "1) Вводная — разговорное лирическое описание без цифр и прогноза.\n"
                    + "2) Форма — общее впечатление словами по form=overall; "
                    + "цифры в этом абзаце бери только из строк FACTS "
                    + "(можно слегка перефразировать, числа не меняй и не добавляй других). "
                    + "Не пиши про домашние игры гостей и гостевые игры хозяев.\n"
                    + "3) Заключение — вероятный счёт из TIP (иначе MS) в виде h:a и коротко почему; "
                    + "цифры из FACTS в этом абзаце можно повторить, новые метрики не выдумывай.\n"
                    + "Запрещено: tip/λ/model/1X2/протокол; стадионы и города; погода; "
                    + "травмы; составы; серии W/D/L; счёты прошлых игр; "
                    + "китайские иероглифы и английские слова вместо русских "
                    + "(названия клубов из M оставляй как есть).";

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
            return "qwen/qwen3.8-27b";
        }
        return model.trim();
    }
}
