package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class League {
    private Long id;
    private String name;
    private String country;
    private String logo;
    private String flag;
    private Long season;
    private String round;
}
