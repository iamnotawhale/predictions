package zhigalin.predictions.dto.predict;

import lombok.*;
import zhigalin.predictions.model.event.Match;

@Value
@Builder
public class OddsDto {

    Long id;

    Double homeChance;

    Double drawChance;

    Double awayChance;

    Long fixtureId;

    Match match;
}
