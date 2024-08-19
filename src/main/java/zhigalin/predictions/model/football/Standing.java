package zhigalin.predictions.model.football;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Standing {

    private int teamId;
    private int points;
    private int games;
    private int won;
    private int drawn;
    private int lost;
    private int goalsFor;
    private int goalsAgainst;

    public int compareGoals(Standing st) {
        return Integer.compare(st.getGoalsFor() - st.getGoalsAgainst(), this.getGoalsFor() - this.getGoalsAgainst());
    }
}
