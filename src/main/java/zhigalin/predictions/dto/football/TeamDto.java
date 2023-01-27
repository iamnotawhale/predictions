package zhigalin.predictions.dto.football;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamDto {

    Long id;

    Long publicId;

    String teamName;

    String code;

    String logo;
}
