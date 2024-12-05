package zhigalin.predictions.model.event;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @JsonProperty("public_id")
    private int publicId;
    @JsonProperty("week_id")
    private int weekId;
    @JsonProperty("home_team_id")
    private int homeTeamId;
    @JsonProperty("away_team_id")
    private int awayTeamId;
    @JsonProperty("home_team_score")
    private Integer homeTeamScore;
    @JsonProperty("away_team_score")
    private Integer awayTeamScore;
    private String result;
    private String status;
    @JsonProperty("local_date_time")
    private LocalDateTime localDateTime;
    @JsonProperty("last_processed_at")
    private LocalDateTime lastProcessedAt;
}
