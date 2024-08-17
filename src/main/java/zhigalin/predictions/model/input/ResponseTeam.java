package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ResponseTeam {
    private int id;
    private String name;
    private String logo;
    private Boolean winner;
    private String code;
}
