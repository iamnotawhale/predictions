package zhigalin.predictions.service.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.util.TeamCodeMapper;

/**
 * League-wide absences without API-Football (100 req/day budget).
 * Primary: FPL {@code bootstrap-static}; fallback ESPN eng.1 injuries.
 * Transfers/loans filtered out. UI gets short name + kind only (no return dates).
 */
@Service
public class InjuryService {
    private static final Logger log = LoggerFactory.getLogger("server");

    public static final String KIND_INJURY = "injury";
    public static final String KIND_SUSPENSION = "suspension";
    public static final String KIND_DOUBT = "doubt";
    public static final String KIND_OTHER = "other";

    private static final String FPL_BOOTSTRAP = "https://fantasy.premierleague.com/api/bootstrap-static/";
    private static final String ESPN_INJURIES =
            "https://site.web.api.espn.com/apis/site/v2/sports/soccer/eng.1/injuries";
    private static final long SUCCESS_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_TTL_MS = 30 * 60 * 1000L;

    private final ObjectMapper mapper;
    private final AtomicReference<CachedSnapshot> cache = new AtomicReference<>();

    /** @param kind one of {@link #KIND_INJURY}, {@link #KIND_SUSPENSION}, {@link #KIND_DOUBT}, {@link #KIND_OTHER} */
    public record InjuryInfo(String teamCode, String playerName, String kind) {
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
                out.add(new InjuryInfo(teamCode, name, kindFromFplStatus(statusCode)));
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
                    if (isTransferOrDeparture(reason)) {
                        continue;
                    }
                    String status = item.path("status").asText("").trim();
                    out.add(new InjuryInfo(teamCode, shortName(name), kindFromEspn(status, reason)));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("ESPN injuries error: {}", e.getMessage());
            return List.of();
        }
    }

    static String kindFromFplStatus(String status) {
        return switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "i" -> KIND_INJURY;
            case "s" -> KIND_SUSPENSION;
            case "d" -> KIND_DOUBT;
            default -> KIND_OTHER;
        };
    }

    static String kindFromEspn(String status, String reason) {
        String blob = ((status == null ? "" : status) + " " + (reason == null ? "" : reason)).toLowerCase(Locale.ROOT);
        if (blob.contains("suspend")) {
            return KIND_SUSPENSION;
        }
        if (blob.contains("doubt") || blob.contains("questionable") || blob.contains("probable")) {
            return KIND_DOUBT;
        }
        if (blob.contains("injur") || blob.contains("concussion") || blob.contains("strain") || blob.contains("fracture")) {
            return KIND_INJURY;
        }
        return KIND_OTHER;
    }

    public static String shortName(String fullName) {
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
}
