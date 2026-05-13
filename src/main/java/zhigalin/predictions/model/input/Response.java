package zhigalin.predictions.model.input;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import zhigalin.predictions.model.event.Lineup;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Response {
    private Fixture fixture;
    private League league;
    private Teams teams;
    private Goals goals;
    private Score score;
    private ResponseTeam team;
    @JsonProperty("startXI")
    private List<Lineup> lineup;
}
