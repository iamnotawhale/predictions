package zhigalin.predictions.dto.football;

import lombok.*;

@Value
@Builder
public class TeamDto {

    Long id;
    String teamName;
    String code;
    String logo;
    Long publicId;
}
