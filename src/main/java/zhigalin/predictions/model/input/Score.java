package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Score {
    private Goals halftime;
    private Goals fulltime;
    private Goals extratime;
    private Goals penalty;
}
