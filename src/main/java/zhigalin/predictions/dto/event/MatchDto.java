package zhigalin.predictions.dto.event;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class MatchDto {
    Long id;
    Long publicId;
    Week week;
    LocalDateTime localDateTime;
    Team homeTeam;
    Team awayTeam;
    Integer homeTeamScore;
    Integer awayTeamScore;
    String result;
    String status;
    List<Prediction> predictions;
}
