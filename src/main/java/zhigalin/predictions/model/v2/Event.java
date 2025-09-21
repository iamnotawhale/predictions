package zhigalin.predictions.model.v2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {
    private String id;
    private String date;
    private String shortName;
    private Status status;
    private List<Competition> competitions;
}
