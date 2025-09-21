package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OddStatV2 {
    private Double value;
}
