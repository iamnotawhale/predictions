package zhigalin.predictions.model.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Week {

    private int id;
    private String name;
    private int seasonId;
    private Boolean isCurrent;
}
