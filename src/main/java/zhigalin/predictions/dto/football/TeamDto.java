package zhigalin.predictions.dto.football;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamDto {

    Long id;

    Long publicId;

    String name;

    String code;

    String logo;
}
