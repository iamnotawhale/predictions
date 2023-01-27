package zhigalin.predictions.dto.news;

import lombok.*;

import java.time.LocalDateTime;

@Value
@Builder
public class NewsDto {

    Long id;

    String title;

    String link;

    LocalDateTime localDateTime;
}
