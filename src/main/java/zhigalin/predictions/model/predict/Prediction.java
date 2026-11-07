package zhigalin.predictions.model.predict;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Prediction {

    private int matchId;
    private Integer cupMatchId;
    private int userId;
    private Integer homeScore;
    private Integer awayScore;
    private Integer points;

    public boolean isCupPrediction() {
        return cupMatchId != null;
    }
}
