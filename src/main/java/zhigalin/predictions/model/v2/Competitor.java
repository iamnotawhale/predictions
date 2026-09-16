package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Competitor {
    private String id;
    private String homeAway;
    private String score;
    private EspnTeamRef team;

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
}
