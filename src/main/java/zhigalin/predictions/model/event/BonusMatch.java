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
public class BonusMatch {

    public static final Comparator<BonusMatch> BY_KICKOFF_THEN_PUBLIC_ID = Comparator
            .comparing(BonusMatch::getLocalDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(BonusMatch::getPublicId);

    @JsonProperty("public_id")
    private int publicId;
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
    @JsonProperty("home_team_score")
    private Integer homeTeamScore;
    @JsonProperty("away_team_score")
    private Integer awayTeamScore;
    private String result;
    private String status;
    @JsonProperty("local_date_time")
    private LocalDateTime localDateTime;
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;
    @JsonProperty("live_score_message_id")
    private Integer liveScoreMessageId;

    public LocalDateTime sortTime() {
        return finishedAt != null ? finishedAt : localDateTime;
    }
}
