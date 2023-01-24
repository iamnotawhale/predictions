package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weeks")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Week {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Week_generator")
    @SequenceGenerator(sequenceName = "Week_sequence", name = "Week_generator", allocationSize = 1)
    private Long id;

    private String weekName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", referencedColumnName = "id")
    private Season season;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "week")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Match> matches;

    private Boolean isCurrent;
}
