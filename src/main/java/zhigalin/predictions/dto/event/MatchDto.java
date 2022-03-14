package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class MatchDto {
    private Long id;
    private Week week;
    private LocalDateTime matchDate;
    private Team homeTeam;
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String result;
    private String status;
}
