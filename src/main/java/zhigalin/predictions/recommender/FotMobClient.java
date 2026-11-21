package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zhigalin.predictions.util.AppTimeZones;

@Component
public class FotMobClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int EPL_LEAGUE_ID = 47;
    private static final String LEAGUE_URL = "https://www.fotmob.com/api/data/leagues";
    private static final String MATCH_URL = "https://www.fotmob.com/api/data/matchDetails";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long LEAGUE_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final long GAP_RETRY_TTL_MS = 20L * 60L * 1000L;

    private final ObjectMapper objectMapper;

    private volatile CachedLeague cache;

    public FotMobClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<FotMobFixture> fetchFinishedFixtures(int seasonStartYear, boolean preferFresh) {
        String season = seasonStartYear + "/" + (seasonStartYear + 1);
        long now = System.currentTimeMillis();
        CachedLeague hit = cache;
        if (hit != null && season.equals(hit.season())) {
            long ttl = preferFresh ? GAP_RETRY_TTL_MS : LEAGUE_TTL_MS;
            if (now - hit.fetchedAtMs() < ttl) {
                return hit.fixtures();
            }
        }
        try {
            HttpResponse<String> response = Unirest.get(LEAGUE_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .queryString("id", EPL_LEAGUE_ID)
                    .queryString("season", season)
                    .asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("FotMob league empty: status={} season={}", response.getStatus(), season);
                return hit != null && season.equals(hit.season()) ? hit.fixtures() : List.of();
            }
            List<FotMobFixture> parsed = parseFixtures(response.getBody());
            if (parsed.isEmpty()) {
                log.warn("FotMob league fixtures empty for season {}", season);
                return hit != null && season.equals(hit.season()) ? hit.fixtures() : List.of();
            }
            cache = new CachedLeague(season, parsed, now);
            return parsed;
        } catch (Exception e) {
            log.warn("FotMob league fetch failed for season {}: {}", season, e.getMessage());
            return hit != null && season.equals(hit.season()) ? hit.fixtures() : List.of();
        }
    }

    public Optional<XgPair> fetchMatchXg(String fotmobMatchId) {
        if (fotmobMatchId == null || fotmobMatchId.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpResponse<String> response = Unirest.get(MATCH_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .queryString("matchId", fotmobMatchId)
                    .asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("FotMob matchDetails empty: status={} matchId={}", response.getStatus(), fotmobMatchId);
                return Optional.empty();
            }
            return parseMatchXg(response.getBody());
        } catch (Exception e) {
            log.warn("FotMob matchDetails failed for {}: {}", fotmobMatchId, e.getMessage());
            return Optional.empty();
        }
    }

    List<FotMobFixture> parseFixtures(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode matches = root.path("fixtures").path("allMatches");
            if (!matches.isArray()) {
                return List.of();
            }
            List<FotMobFixture> out = new ArrayList<>();
            for (JsonNode node : matches) {
                FotMobFixture fixture = toFixture(node);
                if (fixture != null) {
                    out.add(fixture);
                }
            }
            return List.copyOf(out);
        } catch (Exception e) {
            log.warn("FotMob fixtures parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    Optional<XgPair> parseMatchXg(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode periods = root.path("content").path("stats").path("Periods").path("All").path("stats");
            if (!periods.isArray()) {
                return Optional.empty();
            }
            for (JsonNode block : periods) {
                JsonNode stats = block.path("stats");
                if (!stats.isArray()) {
                    continue;
                }
                for (JsonNode stat : stats) {
                    if (!"expected_goals".equals(stat.path("key").asText())) {
                        continue;
                    }
                    if (!"text".equals(stat.path("type").asText()) && !"graph".equals(stat.path("type").asText())) {
                        continue;
                    }
                    Double home = readSide(stat, 0);
                    Double away = readSide(stat, 1);
                    if (home != null && away != null) {
                        return Optional.of(new XgPair(home, away));
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("FotMob xG parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static FotMobFixture toFixture(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode status = node.path("status");
        if (!status.path("finished").asBoolean(false)) {
            return null;
        }
        String id = node.path("id").asText(null);
        Optional<String> homeCode = FotMobTeamNameMapper.toTeamCode(node.path("home").path("name").asText(null));
        Optional<String> awayCode = FotMobTeamNameMapper.toTeamCode(node.path("away").path("name").asText(null));
        if (id == null || id.isBlank() || homeCode.isEmpty() || awayCode.isEmpty()) {
            return null;
        }
        String utc = status.path("utcTime").asText(null);
        if (utc == null || utc.isBlank()) {
            return null;
        }
        try {
            LocalDateTime kickoff = Instant.parse(utc).atZone(AppTimeZones.DISPLAY).toLocalDateTime();
            return new FotMobFixture(id, homeCode.get(), awayCode.get(), kickoff);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double readSide(JsonNode stat, int index) {
        JsonNode rawStats = stat.path("rawStats");
        if (rawStats.isArray() && rawStats.size() > index) {
            JsonNode side = rawStats.get(index);
            if (side != null && side.has("value") && side.get("value").isNumber()) {
                return side.get("value").asDouble();
            }
        }
        JsonNode stats = stat.path("stats");
        if (stats.isArray() && stats.size() > index) {
            JsonNode side = stats.get(index);
            if (side != null && !side.isNull()) {
                if (side.isNumber()) {
                    return side.asDouble();
                }
                String text = side.asText(null);
                if (text != null && !text.isBlank()) {
                    try {
                        return Double.parseDouble(text);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public record FotMobFixture(String fotmobId, String homeCode, String awayCode, LocalDateTime kickoff) {
    }

    public record XgPair(double homeXg, double awayXg) {
    }

    private record CachedLeague(String season, List<FotMobFixture> fixtures, long fetchedAtMs) {
    }
}
