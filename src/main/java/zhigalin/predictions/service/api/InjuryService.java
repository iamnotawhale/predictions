package zhigalin.predictions.service.api;

import java.util.ArrayList;
import java.util.List;
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
 * League-wide injury/availability snapshot without API-Football (100 req/day budget).
 * Primary: Fantasy Premier League {@code bootstrap-static} (1 request → all clubs).
 * Fallback: ESPN eng.1 injuries (often empty).
 */
@Service
public class InjuryService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final String FPL_BOOTSTRAP = "https://fantasy.premierleague.com/api/bootstrap-static/";
    private static final String ESPN_INJURIES =
            "https://site.web.api.espn.com/apis/site/v2/sports/soccer/eng.1/injuries";
    private static final long SUCCESS_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_TTL_MS = 30 * 60 * 1000L;

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
        String home = homeCode.toUpperCase();
        String away = awayCode.toUpperCase();
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
                    teamCodes.put(id, code.toUpperCase());
                }
            }
            List<InjuryInfo> out = new ArrayList<>();
            for (JsonNode el : root.path("elements")) {
                String statusCode = el.path("status").asText("a").trim().toLowerCase();
                if ("a".equals(statusCode)) {
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
                String news = el.path("news").asText("").trim();
                out.add(new InjuryInfo(teamCode, name, translateFplStatus(statusCode), news));
            }
            log.info("FPL injuries: {} unavailable players", out.size());
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
                teamCode = teamCode.toUpperCase();
                for (JsonNode item : teamNode.path("injuries")) {
                    String name = item.path("athlete").path("displayName").asText("").trim();
                    if (name.isBlank()) {
                        continue;
                    }
                    String status = item.path("status").asText("").trim();
                    String reason = item.path("longComment").asText("").trim();
                    if (reason.isBlank()) {
                        reason = item.path("shortComment").asText("").trim();
                    }
                    if (reason.isBlank()) {
                        reason = item.path("details").path("type").asText("").trim();
                    }
                    out.add(new InjuryInfo(teamCode, name, status, reason));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("ESPN injuries error: {}", e.getMessage());
            return List.of();
        }
    }

    private static String translateFplStatus(String status) {
        return switch (status) {
            case "i" -> "травма";
            case "d" -> "под вопросом";
            case "s" -> "дисквалификация";
            case "u" -> "недоступен";
            case "n" -> "вне заявки";
            default -> status;
        };
    }
}
