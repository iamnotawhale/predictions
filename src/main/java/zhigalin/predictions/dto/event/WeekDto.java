package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.event.Season;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class WeekDto {
    private Long id;
    private String weekName;
    private Season season;
    private Boolean isCurrent;
}
