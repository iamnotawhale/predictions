package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Season {
    private Integer year;
    private Integer type;
    private String slug;
}
