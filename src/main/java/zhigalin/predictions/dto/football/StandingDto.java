package zhigalin.predictions.dto.football;

import lombok.*;
import zhigalin.predictions.model.football.Team;

@Value
@Builder
public class StandingDto {

    Team team;

    Integer points;

    Integer games;

    Integer won;

    Integer draw;

    Integer lost;

    Integer goalsScored;

    Integer goalsAgainst;

    public int compareGoals(StandingDto s) {
        return Integer.compare(s.getGoalsScored() - s.getGoalsAgainst(), this.getGoalsScored() - this.getGoalsAgainst());
    }
}
