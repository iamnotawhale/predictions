package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Status {
    @JsonProperty("long")
    private String mylong;
    @JsonProperty("short")
    private String myshort;
    private Integer elapsed;
}
