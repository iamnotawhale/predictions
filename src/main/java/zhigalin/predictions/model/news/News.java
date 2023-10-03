package zhigalin.predictions.model.news;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class News {
    private Long id;
    private String title;
    private String link;
    private LocalDateTime localDateTime;
}
