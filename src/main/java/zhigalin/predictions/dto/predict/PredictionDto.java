package zhigalin.predictions.dto.predict;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.event.Match;

@Value
@Builder
public class PredictionDto {
    Long id;
    Match match;
    UserDto user;
    Integer homeTeamScore;
    Integer awayTeamScore;
    Long points;
}
