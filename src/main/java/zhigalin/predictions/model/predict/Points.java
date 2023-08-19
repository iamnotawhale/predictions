package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "points")
public class Points {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "points_generator")
    @SequenceGenerator(sequenceName = "points_sequence", name = "points_generator", allocationSize = 1)
    private Long id;
    private Long userId;
    private Long value;
}
