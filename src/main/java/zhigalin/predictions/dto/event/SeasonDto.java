package zhigalin.predictions.dto.event;

import lombok.*;

@Value
@Builder
public class SeasonDto {

    Long id;
    String seasonName;
}
