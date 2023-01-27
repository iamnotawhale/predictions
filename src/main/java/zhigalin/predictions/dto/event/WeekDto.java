package zhigalin.predictions.dto.event;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Season;

import java.util.Set;

@Value
@Builder
public class WeekDto {

    Long id;

    String name;

    Season season;

    Set<Match> matches;

    @NonFinal
    @Setter
    Boolean isCurrent;
}
