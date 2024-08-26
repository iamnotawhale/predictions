package zhigalin.predictions.service.odds.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OddsMatch {

    private String id;
    @JsonProperty("sport_key")
    private String sportKey;
    @JsonProperty("sport_title")
    private String sportTitle;
    @JsonProperty("commence_time")
    private String commenceTime;
    @JsonProperty("home_team")
    private String homeTeam;
    @JsonProperty("away_team")
    private String awayTeam;
    private List<Bookmaker> bookmakers;
}
