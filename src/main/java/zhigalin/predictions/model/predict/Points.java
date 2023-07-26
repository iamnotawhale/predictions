package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import zhigalin.predictions.model.event.Season;

import javax.persistence.*;

@Builder
@Data
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", referencedColumnName = "id")
    private Season season;
}
