package zhigalin.predictions.model.v2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Competition {
    private Status status;
    private List<Competitor> competitors;
    private List<OddV2> odds;
}
