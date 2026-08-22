package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Type {
    private String state;
    private String detail;
    private String shortDetail;
    private String description;
    private Boolean completed;
}
