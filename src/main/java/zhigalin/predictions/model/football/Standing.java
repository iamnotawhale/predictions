package zhigalin.predictions.model.football;

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
@Table(name = "standing")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Standing {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Standing_generator")
    @SequenceGenerator(sequenceName = "Standing_sequence", name = "Standing_generator", allocationSize = 1)
    private Long id;

    @OneToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private Integer points;

    private Integer games;

    private Integer won;

    private Integer draw;

    private Integer lost;

    private Integer goalsScored;

    private Integer goalsAgainst;

    private String result;

    public int compareGoals(Standing s) {
        return Integer.compare(s.getGoalsScored() - s.getGoalsAgainst(), this.getGoalsScored() - this.getGoalsAgainst());
    }
}
