package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.event.Week;

import java.util.List;

@Value
@Builder
public class SeasonDto {

    Long id;

    String seasonName;

    List<Week> weeks;
}
