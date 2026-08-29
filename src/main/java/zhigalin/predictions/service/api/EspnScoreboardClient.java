package zhigalin.predictions.service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zhigalin.predictions.model.v2.Scoreboard;

@Component
public class EspnScoreboardClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String SCOREBOARD_URL =
            "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard";

    private final ObjectMapper mapper;

    public EspnScoreboardClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Scoreboard fetchScoreboard() {
        try {
            HttpResponse<String> response = Unirest.get(SCOREBOARD_URL).asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("ESPN scoreboard empty response: status={}", response.getStatus());
                return null;
            }
            return mapper.readValue(response.getBody(), Scoreboard.class);
        } catch (Exception e) {
            log.warn("ESPN scoreboard fetch failed: {}", e.getMessage());
            return null;
        }
    }
}
