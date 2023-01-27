package zhigalin.predictions.dto.football;

import lombok.*;
import zhigalin.predictions.model.football.Team;

@Value
@Builder
public class StandingDto {

    Long id;

    Team team;

    Integer points;

    Integer games;

    Integer won;

    Integer draw;

    Integer lost;

    Integer goalsScored;

    Integer goalsAgainst;

    public int compareGoals(StandingDto dto) {
        return Integer.compare(dto.getGoalsScored() - dto.getGoalsAgainst(), this.getGoalsScored() - this.getGoalsAgainst());
    }
}
