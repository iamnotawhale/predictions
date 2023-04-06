package zhigalin.predictions.dto.event;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.model.event.Week;

import java.util.List;

@Value
@Builder
public class SeasonDto {

    Long id;

    String name;

    List<Week> weeks;
}
