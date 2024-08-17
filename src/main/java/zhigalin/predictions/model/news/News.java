package zhigalin.predictions.model.news;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class News {
    private Long id;
    private String title;
    private String link;
    private LocalDateTime localDateTime;
}
