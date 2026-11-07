package zhigalin.predictions.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.Root;

@Service
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String HOST = "v3.football.api-sports.io";
    private static final String BASE = "https://v3.football.api-sports.io";
    private static final String KEY_HEADER = "x-rapidapi-key";
    private static final String HOST_HEADER = "x-rapidapi-host";
    private static final long MIN_INTERVAL_MS = 350L;

    private final ObjectMapper mapper;
    private final String apiFootballToken;
    private final Object rateLock = new Object();
    private long lastRequestAtMs;

    public ApiFootballClient(
            ObjectMapper mapper,
            @Value("${api.football.token}") String apiFootballToken
    ) {
        this.mapper = mapper;
        this.apiFootballToken = apiFootballToken == null ? "" : apiFootballToken;
    }

    public List<Response> fetchLeagueFixtures(int leagueId, int season) {
        return fetchFixtures(leagueId, season, null, null);
    }

    public List<Response> fetchLeagueFixtures(int leagueId, int season, String from, String to) {
        return fetchFixtures(leagueId, season, from, to);
    }

    public List<Response> fetchLeagueTeams(int leagueId, int season) {
        try {
            HttpResponse<String> resp = get("/teams")
                    .queryString("league", leagueId)
                    .queryString("season", season)
                    .asString();
            return parseResponses(resp, "teams");
        } catch (Exception e) {
            log.error("ApiFootball teams error: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Response> fetchFixtureLineups(int fixtureId) {
        try {
            HttpResponse<String> resp = get("/fixtures/lineups")
                    .queryString("fixture", fixtureId)
                    .queryString("type", "startXI")
                    .asString();
            return parseResponses(resp, "lineups");
        } catch (Exception e) {
            log.error("ApiFootball lineups error: {}", e.getMessage());
            return List.of();
        }
    }

    public JsonNode fetchFixtureStatistics(int fixtureId) {
        try {
            HttpResponse<String> resp = get("/fixtures/statistics")
                    .queryString("fixture", fixtureId)
                    .asString();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
                log.warn("ApiFootball statistics HTTP {} for fixture {}", resp.getStatus(), fixtureId);
                return null;
            }
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            log.warn("ApiFootball statistics error for fixture {}: {}", fixtureId, e.getMessage());
            return null;
        }
    }

    private List<Response> fetchFixtures(int leagueId, int season, String from, String to) {
        try {
            var req = get("/fixtures")
                    .queryString("league", leagueId)
                    .queryString("season", season);
            if (from != null && to != null) {
                req.queryString("from", from).queryString("to", to);
            }
            HttpResponse<String> resp = req.asString();
            return parseResponses(resp, "fixtures");
        } catch (Exception e) {
            log.error("ApiFootball fixtures error: {}", e.getMessage());
            return List.of();
        }
    }

    private kong.unirest.GetRequest get(String path) throws UnirestException {
        throttle();
        if (apiFootballToken.isBlank()) {
            throw new IllegalStateException("api.football.token is empty");
        }
        return Unirest.get(BASE + path)
                .header(KEY_HEADER, apiFootballToken)
                .header(HOST_HEADER, HOST);
    }

    private void throttle() {
        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            long wait = lastRequestAtMs + MIN_INTERVAL_MS - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }

    private List<Response> parseResponses(HttpResponse<String> resp, String label) throws Exception {
        if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().isBlank()) {
            log.warn("ApiFootball {} HTTP {}", label, resp.getStatus());
            return List.of();
        }
        Root root = mapper.readValue(resp.getBody(), Root.class);
        if (root == null || root.getResponse() == null) {
            return List.of();
        }
        return Collections.unmodifiableList(root.getResponse());
    }
}
