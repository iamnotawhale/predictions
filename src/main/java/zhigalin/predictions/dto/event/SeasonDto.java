package zhigalin.predictions.dto.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class SeasonDto {
    private Long id;
    private String seasonName;
}
