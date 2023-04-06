package zhigalin.predictions.dto.predict;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PointsDto {
    Long userId;
    Long value;
}
