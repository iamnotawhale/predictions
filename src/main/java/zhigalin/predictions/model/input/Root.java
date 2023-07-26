package zhigalin.predictions.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Root {
    private ArrayList<Response> response;
}
