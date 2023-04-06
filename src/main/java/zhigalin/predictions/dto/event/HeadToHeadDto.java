package zhigalin.predictions.dto.event;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.model.football.Team;

import java.time.LocalDateTime;

@Value
@Builder
public class HeadToHeadDto {
    Long id;
    Team homeTeam;
    Team awayTeam;
    Integer homeTeamScore;
    Integer awayTeamScore;
    LocalDateTime localDateTime;
    String leagueName;
}
