package zhigalin.predictions.dto.predict;

import lombok.*;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class PredictionDto {
    private Long id;
    private Match match;
    private User user;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
}
