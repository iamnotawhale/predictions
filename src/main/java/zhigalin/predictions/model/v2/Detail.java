package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** One key event (goal, card, …) from {@code competitions[].details} of the ESPN scoreboard. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Detail {
    private Clock clock;
    private TeamRef team;
    private Boolean scoringPlay;
    private Boolean shootout;
    private Boolean ownGoal;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Clock {
        private Double value;
        private String displayValue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamRef {
        private String id;
    }
}
