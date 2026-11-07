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

    public static final Comparator<Match> BY_KICKOFF_THEN_ID = Comparator
            .comparing(Match::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(Match::getId);

    @JsonProperty("id")
    private int id;
    @JsonProperty("week_id")
    private int weekId;
    @JsonProperty("home_team_id")
    private int homeTeamId;
    @JsonProperty("away_team_id")
    private int awayTeamId;
    @JsonProperty("home_score")
    private Integer homeScore;
    @JsonProperty("away_score")
    private Integer awayScore;
    private String result;
    private String status;
    @JsonProperty("starts_at")
    private LocalDateTime startsAt;
    @JsonProperty("last_processed_at")
    private LocalDateTime lastProcessedAt;
    @JsonProperty("espn_id")
    private String espnId;
    @JsonProperty("live_score_message_id")
    private Integer liveScoreMessageId;
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;

    public LocalDateTime sortTime() {
        return finishedAt != null ? finishedAt : startsAt;
    }
}
