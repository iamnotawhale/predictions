package zhigalin.predictions.service.api;

import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.util.EspnTeamLogos;

/**
 * Season team stats from ESPN core API. Cached 6h per ESPN team id.
 */
@Service
public class EspnTeamSeasonStatsService {
    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String STATS_URL =
            "https://sports.core.api.espn.com/v2/sports/soccer/leagues/eng.1/seasons/%d/types/1/teams/%d/statistics";
    private static final long TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_TTL_MS = 30 * 60 * 1000L;

    public record TeamSeasonExtras(
            Double possessionPct,
            Double shotsPerMatch,
            Double shotsOnTargetPerMatch,
            Integer cleanSheets,
            Double avgXg,
            Double avgXga
    ) {
        public boolean isEmpty() {
            return possessionPct == null
                    && shotsPerMatch == null
                    && shotsOnTargetPerMatch == null
                    && cleanSheets == null
                    && avgXg == null
                    && avgXga == null;
        }
    }

    private record CacheEntry(TeamSeasonExtras extras, long fetchedAtMs, boolean success) {
    }

    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    public EspnTeamSeasonStatsService(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    public TeamSeasonExtras forInternalCode(String internalCode) {
        Integer espnId = EspnTeamLogos.espnTeamId(internalCode);
        if (espnId == null) {
            return empty();
        }
        return forEspnTeamId(espnId);
    }

    public TeamSeasonExtras forEspnTeamId(int espnTeamId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(espnTeamId);
        if (cached != null) {
            long ttl = cached.success() ? TTL_MS : FAILURE_TTL_MS;
            if (now - cached.fetchedAtMs() < ttl) {
                return cached.extras();
            }
        }
        TeamSeasonExtras fetched = fetch(espnTeamId);
        boolean ok = !fetched.isEmpty();
        cache.put(espnTeamId, new CacheEntry(fetched, now, ok));
        return fetched;
    }

    private TeamSeasonExtras fetch(int espnTeamId) {
        try {
            int season = DataInitService.SEASON;
            HttpResponse<String> resp = Unirest.get(STATS_URL.formatted(season, espnTeamId))
                    .header("Accept", "application/json")
                    .header("User-Agent", "predictions-bot/1.0")
                    .connectTimeout(3_000)
                    .socketTimeout(6_000)
                    .asString();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
                log.info("ESPN team stats: team={} season={} status={}", espnTeamId, season, resp.getStatus());
                return empty();
            }
            JsonNode categories = mapper.readTree(resp.getBody())
                    .path("splits")
                    .path("categories");
            if (!categories.isArray()) {
                return empty();
            }
            Double possession = null;
            Double totalShots = null;
            Double shotsOnTarget = null;
            Double appearances = null;
            Integer cleanSheets = null;
            Double avgXg = null;
            Double avgXga = null;
            for (JsonNode cat : categories) {
                for (JsonNode stat : cat.path("stats")) {
                    String key = stat.path("name").asText("");
                    JsonNode valueNode = stat.path("value");
                    if (!valueNode.isNumber()) {
                        continue;
                    }
                    double value = valueNode.asDouble();
                    switch (key) {
                        case "possessionPct" -> possession = value;
                        case "totalShots" -> totalShots = value;
                        case "shotsOnTarget" -> shotsOnTarget = value;
                        case "appearances" -> appearances = value;
                        case "cleanSheet" -> cleanSheets = (int) Math.round(value);
                        case "avgExpectedGoals" -> avgXg = value;
                        case "avgExpectedGoalsConceded" -> avgXga = value;
                        default -> {
                        }
                    }
                }
            }
            Double shotsPm = perMatch(totalShots, appearances);
            Double sotPm = perMatch(shotsOnTarget, appearances);
            return new TeamSeasonExtras(possession, shotsPm, sotPm, cleanSheets, avgXg, avgXga);
        } catch (Exception e) {
            log.warn("ESPN team stats error team={}: {}", espnTeamId, e.getMessage());
            return empty();
        }
    }

    private static Double perMatch(Double total, Double appearances) {
        if (total == null || appearances == null || appearances <= 0) {
            return null;
        }
        return total / appearances;
    }

    private static TeamSeasonExtras empty() {
        return new TeamSeasonExtras(null, null, null, null, null, null);
    }
}
