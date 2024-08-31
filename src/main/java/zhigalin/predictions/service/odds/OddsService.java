package zhigalin.predictions.service.odds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.odds.entity.Bookmaker;
import zhigalin.predictions.service.odds.entity.Market;
import zhigalin.predictions.service.odds.entity.OddsMatch;
import zhigalin.predictions.service.odds.entity.Outcome;
import zhigalin.predictions.util.DaoUtil;

@Service
public class OddsService {

    private final PanicSender panicSender;
    @Value("${api.odds.url}")
    private String url;
    @Value("${api.odds.key}")
    private String key;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    public static final Map<Integer, Odd> ODDS = new HashMap<>();

    public OddsService(PanicSender panicSender) {
        this.panicSender = panicSender;
    }

    public void oddsInit(List<Match> matches) {
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .queryString("apiKey", key)
                    .queryString("regions", "eu")
                    .queryString("markets", "h2h")
                    .queryString("dateFormat", "iso")
                    .queryString("oddsFormat", "decimal")
                    .queryString("commenceTimeFrom", date(0))
                    .queryString("commenceTimeTo", date(1))
                    .header("Accept", "application/json")
                    .asString();
            if (response.getStatus() == 200) {
                serverLogger.info("Successfully retrieved odds");
            } else {
                serverLogger.warn("Failed to retrieve odds");
            }
            List<OddsMatch> oddsMatches = mapper.readValue(
                    response.getBody(),
                    mapper.getTypeFactory().constructCollectionType(List.class, OddsMatch.class)
            );
            calculateMatchOdds(matches, oddsMatches);
        } catch (Exception e) {
            String message = "Failed to retrieve odds";
            panicSender.sendPanic(message, e);
        }
    }

    public record Odd(double home, double draw, double away) {
    }

    private String date(int plusDays) {
        LocalDate day = LocalDate.now().plusDays(plusDays);
        LocalDateTime commenceTimeFrom = LocalDateTime.of(day, LocalTime.MIDNIGHT)
                .atZone(ZoneId.of("UTC"))
                .toLocalDateTime();
        return commenceTimeFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    private void calculateMatchOdds(List<Match> matches, List<OddsMatch> oddsMatches) {
        for (OddsMatch oddsMatch : oddsMatches) {
            Match match = matches.stream().filter(m -> {
                        Team homeTeam = DaoUtil.TEAMS.get(m.getHomeTeamId());
                        Team awayTeam = DaoUtil.TEAMS.get(m.getAwayTeamId());
                        return oddsMatch.getHomeTeam().toLowerCase().contains(homeTeam.getName().toLowerCase()) &&
                               oddsMatch.getAwayTeam().toLowerCase().contains(awayTeam.getName().toLowerCase());
                    })
                    .findFirst()
                    .orElse(null);

            if (match != null) {
                Map<String, Double> teamOdds = new HashMap<>();
                teamOdds.put(oddsMatch.getHomeTeam(), 0.0);
                teamOdds.put(oddsMatch.getAwayTeam(), 0.0);
                teamOdds.put("Draw", 0.0);

                for (Bookmaker bookmaker : oddsMatch.getBookmakers()) {
                    for (Market market : bookmaker.getMarkets()) {
                        if (market.getKey().equals("h2h")) {
                            for (Outcome outcome : market.getOutcomes()) {
                                String outcomeName = outcome.getName();
                                teamOdds.put(outcomeName, teamOdds.get(outcomeName) + outcome.getPrice());
                            }
                        }
                    }
                }

                for (Map.Entry<String, Double> entry : teamOdds.entrySet()) {
                    double averageOdd = entry.getValue() / oddsMatch.getBookmakers().size();
                    teamOdds.put(entry.getKey(), averageOdd);
                }

                ODDS.put(
                        match.getPublicId(),
                        new Odd(
                                round(teamOdds.get(oddsMatch.getHomeTeam())),
                                round(teamOdds.get("Draw")),
                                round(teamOdds.get(oddsMatch.getAwayTeam()))
                        )
                );
            }
        }
    }

    public static double round(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
