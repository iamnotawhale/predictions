package zhigalin.predictions.service.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    private static final DateTimeFormatter ESPN_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper mapper;

    public EspnScoreboardClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Today's scoreboard (ESPN default when {@code dates} is omitted). */
    public Scoreboard fetchScoreboard() {
        return fetchScoreboardUrl(SCOREBOARD_URL);
    }

    /** Scoreboard for a single calendar day ({@code YYYYMMDD}). */
    public Scoreboard fetchScoreboard(LocalDate day) {
        if (day == null) {
            return fetchScoreboard();
        }
        return fetchScoreboard(day, day);
    }

    /**
     * Scoreboard for an inclusive date range. ESPN expects {@code dates=YYYYMMDD} or
     * {@code dates=YYYYMMDD-YYYYMMDD}.
     */
    public Scoreboard fetchScoreboard(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return fetchScoreboard();
        }
        LocalDate start = from != null ? from : to;
        LocalDate end = to != null ? to : from;
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        String dates = start.equals(end)
                ? ESPN_DAY.format(start)
                : ESPN_DAY.format(start) + "-" + ESPN_DAY.format(end);
        return fetchScoreboardUrl(SCOREBOARD_URL + "?dates=" + dates);
    }

    private Scoreboard fetchScoreboardUrl(String url) {
        try {
            HttpResponse<String> response = Unirest.get(url).asString();
            if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("ESPN scoreboard empty response: status={} url={}", response.getStatus(), url);
                return null;
            }
            return mapper.readValue(response.getBody(), Scoreboard.class);
        } catch (Exception e) {
            log.warn("ESPN scoreboard fetch failed: {} url={}", e.getMessage(), url);
            return null;
        }
    }
}
