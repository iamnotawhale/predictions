package zhigalin.predictions.model.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HeadToHead {

    private int homeTeamId;
    private int awayTeamId;
    private int homeTeamScore;
    private int awayTeamScore;
    private LocalDateTime localDateTime;
    private String leagueName;
}
