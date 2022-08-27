package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "odds")
public class Odds {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Odds_generator")
    @SequenceGenerator(sequenceName = "Odds_sequence", name = "Odds_generator", allocationSize = 1)
    private Long id;
    private Double homeChance;
    private Double drawChance;
    private Double awayChance;
    private Long fixtureId;
}