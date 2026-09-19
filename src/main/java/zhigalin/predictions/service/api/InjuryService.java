package zhigalin.predictions.service.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.util.TeamCodeMapper;

/**
 * League-wide injury/availability snapshot without API-Football (100 req/day budget).
 * Primary: Fantasy Premier League {@code bootstrap-static} (1 request → all clubs).
 * Fallback: ESPN eng.1 injuries (often empty).
 * Transfers/loans/departures are filtered out; reasons are localized to Russian.
 */
@Service
public class InjuryService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final String FPL_BOOTSTRAP = "https://fantasy.premierleague.com/api/bootstrap-static/";
    private static final String ESPN_INJURIES =
            "https://site.web.api.espn.com/apis/site/v2/sports/soccer/eng.1/injuries";
    private static final long SUCCESS_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_TTL_MS = 30 * 60 * 1000L;

    private static final Pattern INJURY_CHANCE = Pattern.compile(
            "(?i)^(.+?)\\s+injury\\s*-\\s*(\\d+)%\\s+chance of playing\\.?$");
    private static final Pattern INJURY_EXPECTED = Pattern.compile(
            "(?i)^(.+?)\\s+injury\\s*-\\s*Expected back\\s+(.+)$");
    private static final Pattern INJURY_UNKNOWN = Pattern.compile(
            "(?i)^(.+?)\\s+injury\\s*-\\s*Unknown return date\\.?$");
    private static final Pattern CONCUSSION_EXPECTED = Pattern.compile(
            "(?i)^Concussion\\s*-\\s*Expected back\\s+(.+)$");
    private static final Pattern CONCUSSION_UNKNOWN = Pattern.compile(
            "(?i)^Concussion\\s*-\\s*Unknown return date\\.?$");
    private static final Pattern CONCUSSION_CHANCE = Pattern.compile(
            "(?i)^Concussion\\s*-\\s*(\\d+)%\\s+chance of playing\\.?$");
    private static final Pattern SUSPENDED = Pattern.compile(
            "(?i)^Suspended until\\s+(.+)$");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(?i)^(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");

    private final ObjectMapper mapper;
    private final AtomicReference<CachedSnapshot> cache = new AtomicReference<>();

    public record InjuryInfo(String teamCode, String playerName, String status, String reason) {
    }

    private record CachedSnapshot(List<InjuryInfo> injuries, long fetchedAtMs, boolean success) {
    }

    public InjuryService(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    public List<InjuryInfo> forTeams(String homeCode, String awayCode) {
        List<InjuryInfo> all = snapshot();
        if (all.isEmpty() || homeCode == null || awayCode == null) {
            return List.of();
        }
        String home = homeCode.toUpperCase(Locale.ROOT);
        String away = awayCode.toUpperCase(Locale.ROOT);
        return all.stream()
                .filter(i -> home.equals(i.teamCode()) || away.equals(i.teamCode()))
                .toList();
    }

    public List<InjuryInfo> snapshot() {
        long now = System.currentTimeMillis();
        CachedSnapshot cached = cache.get();
        if (cached != null) {
            long ttl = cached.success() ? SUCCESS_TTL_MS : FAILURE_TTL_MS;
            if (now - cached.fetchedAtMs() < ttl) {
                return cached.injuries();
            }
        }
        List<InjuryInfo> fromFpl = fetchFpl();
        if (!fromFpl.isEmpty()) {
            List<InjuryInfo> frozen = List.copyOf(fromFpl);
            cache.set(new CachedSnapshot(frozen, now, true));
            return frozen;
        }
        List<InjuryInfo> fromEspn = fetchEspn();
        boolean ok = !fromEspn.isEmpty();
        List<InjuryInfo> frozen = List.copyOf(fromEspn);
        cache.set(new CachedSnapshot(frozen, now, ok));
        return frozen;
    }

    private List<InjuryInfo> fetchFpl() {
        try {
            HttpResponse<String> resp = Unirest.get(FPL_BOOTSTRAP)
                    .header("Accept", "application/json")
                    .header("User-Agent", "predictions-bot/1.0")
                    .asString();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
                log.info("FPL injuries: status={}", resp.getStatus());
                return List.of();
            }
            JsonNode root = mapper.readTree(resp.getBody());
            Map<Integer, String> teamCodes = new java.util.HashMap<>();
            for (JsonNode team : root.path("teams")) {
                int id = team.path("id").asInt(0);
                String shortName = team.path("short_name").asText("").trim();
                String code = TeamCodeMapper.toInternalCode(shortName);
                if (id > 0 && code != null && !code.isBlank()) {
                    teamCodes.put(id, code.toUpperCase(Locale.ROOT));
                }
            }
            List<InjuryInfo> out = new ArrayList<>();
            int skippedTransfers = 0;
            for (JsonNode el : root.path("elements")) {
                String statusCode = el.path("status").asText("a").trim().toLowerCase(Locale.ROOT);
                if ("a".equals(statusCode)) {
                    continue;
                }
                String news = el.path("news").asText("").trim();
                if (isTransferOrDeparture(news) || isTransferStatus(statusCode, news)) {
                    skippedTransfers++;
                    continue;
                }
                int teamId = el.path("team").asInt(0);
                String teamCode = teamCodes.get(teamId);
                if (teamCode == null) {
                    continue;
                }
                String name = el.path("web_name").asText("").trim();
                if (name.isBlank()) {
                    name = el.path("second_name").asText("").trim();
                }
                if (name.isBlank()) {
                    continue;
                }
                String statusRu = translateFplStatus(statusCode);
                String reasonRu = translateReason(news);
                if (reasonRu.isBlank()) {
                    reasonRu = statusRu;
                }
                out.add(new InjuryInfo(teamCode, name, statusRu, reasonRu));
            }
            log.info("FPL injuries: {} relevant, skipped {} transfers/loans", out.size(), skippedTransfers);
            return out;
        } catch (Exception e) {
            log.warn("FPL injuries error: {}", e.getMessage());
            return List.of();
        }
    }

    private List<InjuryInfo> fetchEspn() {
        try {
            HttpResponse<String> resp = Unirest.get(ESPN_INJURIES)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .asString();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
                return List.of();
            }
            JsonNode root = mapper.readTree(resp.getBody());
            JsonNode injuries = root.path("injuries");
            if (!injuries.isArray() || injuries.isEmpty()) {
                return List.of();
            }
            List<InjuryInfo> out = new ArrayList<>();
            for (JsonNode teamNode : injuries) {
                String abbr = teamNode.path("team").path("abbreviation").asText("").trim();
                String teamCode = TeamCodeMapper.toInternalCode(abbr);
                if (teamCode == null || teamCode.isBlank()) {
                    continue;
                }
                teamCode = teamCode.toUpperCase(Locale.ROOT);
                for (JsonNode item : teamNode.path("injuries")) {
                    String name = item.path("athlete").path("displayName").asText("").trim();
                    if (name.isBlank()) {
                        continue;
                    }
                    String reason = item.path("longComment").asText("").trim();
                    if (reason.isBlank()) {
                        reason = item.path("shortComment").asText("").trim();
                    }
                    if (reason.isBlank()) {
                        reason = item.path("details").path("type").asText("").trim();
                    }
                    if (isTransferOrDeparture(reason)) {
                        continue;
                    }
                    String status = item.path("status").asText("").trim();
                    String reasonRu = translateReason(reason.isBlank() ? status : reason);
                    out.add(new InjuryInfo(
                            teamCode,
                            name,
                            status.isBlank() ? "травма" : translateReason(status),
                            reasonRu.isBlank() ? "травма" : reasonRu
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("ESPN injuries error: {}", e.getMessage());
            return List.of();
        }
    }

    /** FPL {@code u} is usually a transferred/loaned player; keep only clear absence phrases. */
    static boolean isTransferStatus(String statusCode, String news) {
        if (!"u".equals(statusCode)) {
            return false;
        }
        if (news == null || news.isBlank()) {
            return true;
        }
        String low = news.toLowerCase(Locale.ROOT);
        boolean absenceLike = low.contains("not included in squad")
                || low.contains("unavailable")
                || low.contains("illness")
                || low.contains("personal reasons");
        return !absenceLike;
    }

    static boolean isTransferOrDeparture(String news) {
        if (news == null || news.isBlank()) {
            return false;
        }
        String n = news.toLowerCase(Locale.ROOT);
        return n.contains("has joined")
               || n.contains("joined ")
               || n.contains("on loan")
               || n.contains("permanently")
               || n.contains("permanent transfer")
               || n.contains("departed")
               || n.contains("free agent")
               || n.contains("returned to")
               || n.contains("transferred")
               || n.contains("has left")
               || n.contains("left the club")
               || n.contains("released by")
               || n.contains("sold to");
    }

    static String translateFplStatus(String status) {
        return switch (status) {
            case "i" -> "травма";
            case "d" -> "под вопросом";
            case "s" -> "дисквалификация";
            case "u" -> "недоступен";
            case "n" -> "вне заявки";
            default -> status;
        };
    }

    static String translateReason(String news) {
        if (news == null || news.isBlank()) {
            return "";
        }
        String trimmed = news.trim();

        Matcher m = INJURY_CHANCE.matcher(trimmed);
        if (m.matches()) {
            return "Травма " + translateBodyPart(m.group(1)) + " — шанс сыграть " + m.group(2) + "%";
        }
        m = INJURY_EXPECTED.matcher(trimmed);
        if (m.matches()) {
            return "Травма " + translateBodyPart(m.group(1)) + " — ориентир " + translateDate(m.group(2).trim());
        }
        m = INJURY_UNKNOWN.matcher(trimmed);
        if (m.matches()) {
            return "Травма " + translateBodyPart(m.group(1)) + " — дата возвращения неизвестна";
        }
        m = CONCUSSION_CHANCE.matcher(trimmed);
        if (m.matches()) {
            return "Сотрясение — шанс сыграть " + m.group(1) + "%";
        }
        m = CONCUSSION_EXPECTED.matcher(trimmed);
        if (m.matches()) {
            return "Сотрясение — ориентир " + translateDate(m.group(1).trim());
        }
        m = CONCUSSION_UNKNOWN.matcher(trimmed);
        if (m.matches()) {
            return "Сотрясение — дата возвращения неизвестна";
        }
        m = SUSPENDED.matcher(trimmed);
        if (m.matches()) {
            return "Дисквалификация до " + translateDate(m.group(1).trim());
        }
        String low = trimmed.toLowerCase(Locale.ROOT);
        if (low.contains("not included in squad")) {
            return "Не в заявке";
        }
        if (low.equals("illness") || low.startsWith("illness ")) {
            return "Болезнь";
        }
        return trimmed;
    }

    private static String translateBodyPart(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "achilles" -> "ахилла";
            case "ankle" -> "голеностопа";
            case "arm" -> "руки";
            case "back" -> "спины";
            case "calf" -> "икры";
            case "foot" -> "стопы";
            case "groin" -> "паха";
            case "hamstring" -> "задней поверхности бедра";
            case "knee" -> "колена";
            case "leg" -> "ноги";
            case "thigh" -> "бедра";
            case "shoulder" -> "плеча";
            case "hip" -> "бедра/таза";
            case "head" -> "головы";
            case "unspecified" -> "(не уточнено)";
            default -> key.isBlank() ? "(не уточнено)" : raw.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static String translateDate(String raw) {
        Matcher m = DAY_MONTH.matcher(raw.trim());
        if (!m.find()) {
            return raw;
        }
        String month = switch (m.group(2).substring(0, 1).toUpperCase(Locale.ROOT)
                + m.group(2).substring(1).toLowerCase(Locale.ROOT)) {
            case "Jan" -> "янв";
            case "Feb" -> "фев";
            case "Mar" -> "мар";
            case "Apr" -> "апр";
            case "May" -> "мая";
            case "Jun" -> "июн";
            case "Jul" -> "июл";
            case "Aug" -> "авг";
            case "Sep" -> "сен";
            case "Oct" -> "окт";
            case "Nov" -> "ноя";
            case "Dec" -> "дек";
            default -> m.group(2);
        };
        // May needs genitive "мая" already; others stay short.
        return m.group(1) + " " + month;
    }
}
