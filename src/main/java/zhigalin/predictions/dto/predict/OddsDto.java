package zhigalin.predictions.dto.predict;

import lombok.*;

@Value
@Builder
public class OddsDto {

    Long id;
    Double homeChance;
    Double drawChance;
    Double awayChance;
    Long fixtureId;
}
