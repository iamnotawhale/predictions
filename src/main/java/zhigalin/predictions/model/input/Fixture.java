package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Fixture {
    @JsonProperty("id")
    private Long publicId;
    private Long timestamp;
    private Status status;
}
