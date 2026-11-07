package zhigalin.predictions.model.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HeadToHead {

    private int homeTeamId;
    private int awayTeamId;
    private int homeScore;
    private int awayScore;
    private LocalDateTime startsAt;
    private String leagueName;
}
