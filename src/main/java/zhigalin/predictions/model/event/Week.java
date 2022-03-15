package zhigalin.predictions.model.event;

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
@Table(name = "weeks")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Week {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Week_generator")
    @SequenceGenerator(sequenceName = "Week_sequence", name = "Week_generator")
    private Long id;

    private String weekName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season;

    private Boolean isCurrent;
}
