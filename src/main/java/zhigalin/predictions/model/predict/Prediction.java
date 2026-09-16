package zhigalin.predictions.model.predict;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Prediction {

    /** EPL match public_id; 0 / unused when {@link #bonusMatchId} is set. */
    private int matchPublicId;
    private Integer bonusMatchId;
    private int userId;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private Integer points;

    public boolean isCupPrediction() {
        return bonusMatchId != null;
    }
}
