package zhigalin.predictions.model.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "news")
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "News_generator")
    @SequenceGenerator(sequenceName = "News_sequence", name = "News_generator", allocationSize = 1)
    private Long id;
    private String title;
    private String link;
    private LocalDateTime localDateTime;
}
