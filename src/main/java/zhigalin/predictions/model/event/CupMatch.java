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
public class CupMatch {

    public static final Comparator<CupMatch> BY_KICKOFF_THEN_ID = Comparator
            .comparing(CupMatch::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(CupMatch::getId);

    @JsonProperty("id")
    private int id;
    private String competition;
    @JsonProperty("espn_id")
    private String espnId;
    @JsonProperty("home_team_id")
    private Integer homeTeamId;
    @JsonProperty("away_team_id")
    private Integer awayTeamId;
    @JsonProperty("home_name")
    private String homeName;
    @JsonProperty("away_name")
    private String awayName;
    @JsonProperty("home_logo_url")
    private String homeLogoUrl;
    @JsonProperty("away_logo_url")
    private String awayLogoUrl;
    @JsonProperty("home_espn_code")
    private String homeEspnCode;
    @JsonProperty("away_espn_code")
    private String awayEspnCode;
    @JsonProperty("home_score")
    private Integer homeScore;
    @JsonProperty("away_score")
    private Integer awayScore;
    @JsonProperty("aet_home")
    private Integer aetHome;
    @JsonProperty("aet_away")
    private Integer aetAway;
    @JsonProperty("pen_home")
    private Integer penHome;
    @JsonProperty("pen_away")
    private Integer penAway;
    private String result;
    private String status;
    @JsonProperty("starts_at")
    private LocalDateTime startsAt;
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;
    @JsonProperty("live_score_message_id")
    private Integer liveScoreMessageId;

    public LocalDateTime sortTime() {
        return finishedAt != null ? finishedAt : startsAt;
    }
}
