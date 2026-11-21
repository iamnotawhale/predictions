package zhigalin.predictions.model.v2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Competitor {
    private String id;
    private String homeAway;
    private String score;
    private Integer shootoutScore;
    private EspnTeamRef team;
    private List<LineScore> linescores;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EspnTeamRef {
        private String id;
        private String abbreviation;
        private String displayName;
        private String name;
        private String shortDisplayName;
        private String logo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineScore {
        private String displayValue;
        private Double value;
    }
}
