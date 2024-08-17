package zhigalin.predictions.model.predict;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Prediction {

    private int matchPublicId;
    private int userId;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private int points;
}
