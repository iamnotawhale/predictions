package zhigalin.predictions.model.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Match {

    private int publicId;
    private int weekId;
    private int homeTeamId;
    private int awayTeamId;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String result;
    private String status;
    private LocalDateTime localDateTime;
}
