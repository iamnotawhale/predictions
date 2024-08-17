package zhigalin.predictions.model.football;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Team {

    private int publicId;
    private String name;
    private String code;
    private String logo;
}
