package zhigalin.predictions.dto.event;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Odds;
import zhigalin.predictions.model.predict.Prediction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

@Value
@Builder
public class MatchDto {

    Long id;
    Long publicId;
    Week week;
    LocalDateTime localDateTime;
    LocalDate matchDate;
    LocalTime matchTime;
    Team homeTeam;
    Team awayTeam;
    Integer homeTeamScore;
    Integer awayTeamScore;
    String result;
    String status;
    Set<Prediction> predictions;
    @NonFinal
    @Setter
    Odds odds;
}
