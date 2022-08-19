package zhigalin.predictions.dto.football;

import lombok.*;
import zhigalin.predictions.model.football.Team;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class StandingDto {
    private Long id;
    private Team team;
    private Integer points;
    private Integer games;
    private Integer won;
    private Integer draw;
    private Integer lost;
    private Integer goalsScored;
    private Integer goalsAgainst;
    private String result;
    public int compareGoals(StandingDto s) {
        return Integer.compare(s.getGoalsScored() - s.getGoalsAgainst(), this.getGoalsScored() - this.getGoalsAgainst());
    }
}
