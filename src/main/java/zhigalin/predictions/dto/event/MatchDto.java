package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.event.Stats;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.model.football.Team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class MatchDto {
    private Long id;
    private Long publicId;
    private Week week;
    private LocalDateTime localDateTime;
    private LocalDate matchDate;
    private LocalTime matchTime;
    private Team homeTeam;
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String result;
    private String status;
    private Stats homeStats;
    private Stats awayStats;
}
