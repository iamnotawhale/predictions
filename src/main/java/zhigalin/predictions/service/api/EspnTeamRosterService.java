package zhigalin.predictions.service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.util.EspnTeamLogos;

/**
 * Season player stats from ESPN team roster (eng.1). Cached 6h per ESPN team id.
 */
@Service
public class EspnTeamRosterService {
    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String ROSTER_URL =
            "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/teams/%d/roster";
    private static final long TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_TTL_MS = 30 * 60 * 1000L;

    public record PlayerSeasonStats(
            String name,
            String position,
            int appearances,
            int goals,
            int assists,
            Integer saves
    ) {
    }

    private record CacheEntry(List<PlayerSeasonStats> players, long fetchedAtMs, boolean success) {
    }

    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    public EspnTeamRosterService(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    public List<PlayerSeasonStats> playersForInternalCode(String internalCode) {
        Integer espnId = EspnTeamLogos.espnTeamId(internalCode);
        if (espnId == null) {
            return List.of();
        }
        return playersForEspnTeamId(espnId);
    }

    public List<PlayerSeasonStats> playersForEspnTeamId(int espnTeamId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(espnTeamId);
        if (cached != null) {
            long ttl = cached.success() ? TTL_MS : FAILURE_TTL_MS;
            if (now - cached.fetchedAtMs() < ttl) {
                return cached.players();
            }
        }
        List<PlayerSeasonStats> fetched = fetchRoster(espnTeamId);
        boolean ok = !fetched.isEmpty();
        List<PlayerSeasonStats> frozen = List.copyOf(fetched);
        cache.put(espnTeamId, new CacheEntry(frozen, now, ok));
        return frozen;
    }

    /** Top outfield by goals then assists; include GK with saves if any. */
    public List<PlayerSeasonStats> leadersForInternalCode(String internalCode, int limit) {
        List<PlayerSeasonStats> all = playersForInternalCode(internalCode);
        if (all.isEmpty()) {
            return List.of();
        }
        List<PlayerSeasonStats> outfield = all.stream()
                .filter(p -> p.appearances() > 0 || p.goals() > 0 || p.assists() > 0)
                .filter(p -> !"G".equalsIgnoreCase(p.position()))
                .sorted(Comparator
                        .comparingInt(PlayerSeasonStats::goals).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerSeasonStats::assists).reversed())
                        .thenComparing(Comparator.comparingInt(PlayerSeasonStats::appearances).reversed())
                        .thenComparing(PlayerSeasonStats::name))
                .limit(Math.max(1, limit))
                .toList();
        List<PlayerSeasonStats> keepers = all.stream()
                .filter(p -> "G".equalsIgnoreCase(p.position()))
                .filter(p -> p.appearances() > 0 || (p.saves() != null && p.saves() > 0))
                .sorted(Comparator
                        .comparingInt((PlayerSeasonStats p) -> p.saves() != null ? p.saves() : 0).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerSeasonStats::appearances).reversed()))
                .limit(2)
                .toList();
        List<PlayerSeasonStats> combined = new ArrayList<>(outfield.size() + keepers.size());
        combined.addAll(outfield);
        for (PlayerSeasonStats gk : keepers) {
            if (combined.stream().noneMatch(p -> p.name().equals(gk.name()))) {
                combined.add(gk);
            }
        }
        return List.copyOf(combined);
    }

    private List<PlayerSeasonStats> fetchRoster(int espnTeamId) {
        try {
            HttpResponse<String> resp = Unirest.get(ROSTER_URL.formatted(espnTeamId))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .connectTimeout(3_000)
                    .socketTimeout(6_000)
                    .asString();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
                log.info("ESPN roster: team={} status={}", espnTeamId, resp.getStatus());
                return List.of();
            }
            JsonNode root = mapper.readTree(resp.getBody());
            JsonNode athletes = root.path("athletes");
            if (!athletes.isArray()) {
                return List.of();
            }
            List<PlayerSeasonStats> out = new ArrayList<>();
            for (JsonNode athlete : athletes) {
                PlayerSeasonStats row = parseAthlete(athlete);
                if (row != null) {
                    out.add(row);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("ESPN roster error team={}: {}", espnTeamId, e.getMessage());
            return List.of();
        }
    }

    private static PlayerSeasonStats parseAthlete(JsonNode athlete) {
        String name = athlete.path("shortName").asText("").trim();
        if (name.isBlank()) {
            name = athlete.path("displayName").asText("").trim();
        }
        if (name.isBlank()) {
            return null;
        }
        String position = athlete.path("position").path("abbreviation").asText("").trim().toUpperCase(Locale.ROOT);
        JsonNode categories = athlete.path("statistics").path("splits").path("categories");
        int appearances = 0;
        int goals = 0;
        int assists = 0;
        Integer saves = null;
        if (categories.isArray()) {
            for (JsonNode cat : categories) {
                for (JsonNode stat : cat.path("stats")) {
                    String key = stat.path("name").asText("");
                    double value = stat.path("value").asDouble(0);
                    int iv = (int) Math.round(value);
                    switch (key) {
                        case "appearances" -> appearances = iv;
                        case "totalGoals" -> goals = iv;
                        case "goalAssists" -> assists = iv;
                        case "saves" -> saves = iv;
                        default -> {
                        }
                    }
                }
            }
        }
        return new PlayerSeasonStats(name, position, appearances, goals, assists, saves);
    }
}
