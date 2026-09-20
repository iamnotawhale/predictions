package zhigalin.predictions.service.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private static final String BASE = "https://site.web.api.espn.com/apis/site/v2/sports/soccer/";
    public static final String LEAGUE_EPL = "eng.1";
    public static final List<String> CUP_LEAGUES = List.of(
            "eng.fa",
            "eng.league_cup",
            "uefa.champions",
            "uefa.europa",
            "uefa.europa.conf"
    );
    private static final DateTimeFormatter ESPN_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper mapper;

    public EspnScoreboardClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Scoreboard fetchScoreboard() {
        return fetchScoreboard(LEAGUE_EPL);
    }

    public Scoreboard fetchScoreboard(String leagueSlug) {
        return fetchScoreboardUrl(BASE + leagueSlug + "/scoreboard");
    }

    public Scoreboard fetchScoreboard(LocalDate day) {
        return fetchScoreboard(LEAGUE_EPL, day, day);
    }

    public Scoreboard fetchScoreboard(LocalDate from, LocalDate to) {
        return fetchScoreboard(LEAGUE_EPL, from, to);
    }

    public Scoreboard fetchScoreboard(String leagueSlug, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return fetchScoreboard(leagueSlug);
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
        return fetchScoreboardUrl(BASE + leagueSlug + "/scoreboard?dates=" + dates);
    }

    private Scoreboard fetchScoreboardUrl(String url) {
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "predictions-bot/1.0")
                    .asString();
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
