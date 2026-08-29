package zhigalin.predictions.model.event;

import java.time.LocalDateTime;
import java.util.Comparator;

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

    /** Canonical list order: kickoff ascending, then publicId. */
    public static final Comparator<Match> BY_KICKOFF_THEN_PUBLIC_ID = Comparator
            .comparing(Match::getLocalDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(Match::getPublicId);

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
    @JsonProperty("espn_id")
    private String espnId;
    @JsonProperty("live_score_message_id")
    private Integer liveScoreMessageId;
}
