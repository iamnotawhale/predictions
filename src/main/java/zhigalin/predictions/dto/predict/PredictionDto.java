package zhigalin.predictions.dto.predict;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

@Value
@Builder
public class PredictionDto {
    Long id;
    Match match;
    User user;
    Integer homeTeamScore;
    Integer awayTeamScore;
    Long points;
}
