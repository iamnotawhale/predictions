package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UnderstatClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String LEAGUE_URL = "https://understat.com/league/EPL/";
    private static final Pattern DATES_DATA = Pattern.compile(
            "datesData\\s*=\\s*JSON\\.parse\\('(.+?)'\\)",
            Pattern.DOTALL
    );
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long FRESH_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final long GAP_RETRY_TTL_MS = 20L * 60L * 1000L;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;

    private volatile CachedLeague cache;

    public UnderstatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<UnderstatMatch> fetchFinishedWithXg(int seasonYear, boolean preferFresh) {
        long now = System.currentTimeMillis();
        CachedLeague hit = cache;
        if (hit != null && hit.seasonYear() == seasonYear) {
            long ttl = preferFresh ? GAP_RETRY_TTL_MS : FRESH_TTL_MS;
            if (now - hit.fetchedAtMs() < ttl) {
                return hit.matches();
            }
        }
        try {
            HttpResponse<String> response = Unirest.get(LEAGUE_URL + seasonYear)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", USER_AGENT)
                    .asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("Understat league page empty: status={} season={}", response.getStatus(), seasonYear);
                return hit != null && hit.seasonYear() == seasonYear ? hit.matches() : List.of();
            }
            String body = response.getBody();
            if (looksOffline(body)) {
                log.warn("Understat league page offline/maintenance for season {}", seasonYear);
                return hit != null && hit.seasonYear() == seasonYear ? hit.matches() : List.of();
            }
            List<UnderstatMatch> parsed = parseDatesData(body);
            if (parsed.isEmpty()) {
                log.warn("Understat datesData missing or empty for season {}", seasonYear);
                return hit != null && hit.seasonYear() == seasonYear ? hit.matches() : List.of();
            }
            cache = new CachedLeague(seasonYear, parsed, now);
            return parsed;
        } catch (Exception e) {
            log.warn("Understat fetch failed for season {}: {}", seasonYear, e.getMessage());
            return hit != null && hit.seasonYear() == seasonYear ? hit.matches() : List.of();
        }
    }

    static boolean looksOffline(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String lower = html.toLowerCase();
        return lower.contains("we will come back soon")
                || (lower.contains("errors.css") && !lower.contains("datesdata"));
    }

    List<UnderstatMatch> parseDatesData(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Matcher matcher = DATES_DATA.matcher(html);
        if (!matcher.find()) {
            return List.of();
        }
        try {
            String json = unescapeUnderstatJson(matcher.group(1));
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<UnderstatMatch> out = new ArrayList<>();
            for (JsonNode node : root) {
                UnderstatMatch match = toMatch(node);
                if (match != null) {
                    out.add(match);
                }
            }
            return List.copyOf(out);
        } catch (Exception e) {
            log.warn("Understat datesData parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static UnderstatMatch toMatch(JsonNode node) {
        if (node == null || !node.path("isResult").asBoolean(false)) {
            return null;
        }
        Optional<String> homeCode = UnderstatTeamNameMapper.toTeamCode(node.path("h").path("title").asText(null));
        Optional<String> awayCode = UnderstatTeamNameMapper.toTeamCode(node.path("a").path("title").asText(null));
        if (homeCode.isEmpty() || awayCode.isEmpty()) {
            return null;
        }
        Double homeXg = parseXg(node.path("xG").path("h"));
        Double awayXg = parseXg(node.path("xG").path("a"));
        if (homeXg == null || awayXg == null) {
            return null;
        }
        LocalDateTime kickoff = parseDatetime(node.path("datetime").asText(null));
        if (kickoff == null) {
            return null;
        }
        return new UnderstatMatch(homeCode.get(), awayCode.get(), homeXg, awayXg, kickoff);
    }

    private static Double parseXg(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseDatetime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), DATETIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String unescapeUnderstatJson(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c != '\\' || i + 1 >= encoded.length()) {
                out.append(c);
                continue;
            }
            char next = encoded.charAt(i + 1);
            if (next == 'x' && i + 3 < encoded.length()) {
                out.append((char) Integer.parseInt(encoded.substring(i + 2, i + 4), 16));
                i += 3;
                continue;
            }
            if (next == 'u' && i + 5 < encoded.length()) {
                out.append((char) Integer.parseInt(encoded.substring(i + 2, i + 6), 16));
                i += 5;
                continue;
            }
            switch (next) {
                case '\'' -> out.append('\'');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                default -> out.append(next);
            }
            i++;
        }
        return out.toString();
    }

    public record UnderstatMatch(
            String homeCode,
            String awayCode,
            double homeXg,
            double awayXg,
            LocalDateTime kickoff
    ) {
    }

    private record CachedLeague(int seasonYear, List<UnderstatMatch> matches, long fetchedAtMs) {
    }
}
