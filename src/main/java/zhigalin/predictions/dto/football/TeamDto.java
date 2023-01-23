package zhigalin.predictions.dto.football;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamDto {

    Long id;

    String teamName;

    String code;

    String logo;

    Long publicId;
}
