package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Response {
    private Fixture fixture;
    private League league;
    private Teams teams;
    private Goals goals;
    private Score score;
    private Team team;
}
