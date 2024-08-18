package zhigalin.predictions.model.predict;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Points {

    private String login;
    private int value;
}
