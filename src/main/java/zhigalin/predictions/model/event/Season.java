package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.predict.Prediction;

import javax.persistence.*;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "seasons")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Season {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Season_generator")
    @SequenceGenerator(sequenceName = "Season_sequence", name = "Season_generator", allocationSize = 1)
    private Long id;
    private String name;
    @ToString.Exclude
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "season")
    private List<Week> weeks;
    @ToString.Exclude
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "season")
    private List<Prediction> predicts;
    @ToString.Exclude
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "season")
    private List<Standing> standings;
    private Boolean isCurrent;
}
