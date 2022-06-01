package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.football.Team;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class HeadToHeadDto {
    private Long id;
    private Team homeTeam;
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private LocalDateTime localDateTime;
    private String leagueName;
}
