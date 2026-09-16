package zhigalin.predictions.model.v2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scoreboard {
    private Season season;
    private List<Event> events;
}
