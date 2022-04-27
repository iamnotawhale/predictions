package zhigalin.predictions.dto.news;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class NewsDto {
    private Long id;
    private String title;
    private String link;
    private LocalDateTime dateTime;
}
