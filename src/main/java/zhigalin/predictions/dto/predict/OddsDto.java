package zhigalin.predictions.dto.predict;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class OddsDto {
    private Long id;
    private Double homeChance;
    private Double drawChance;
    private Double awayChance;
    private Long fixtureId;
}
