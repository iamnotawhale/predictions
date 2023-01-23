package zhigalin.predictions.model.football;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "standing")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Standing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "standing_generator")
    @SequenceGenerator(sequenceName = "standing_sequence", name = "standing_generator", allocationSize = 1)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    private Team team;

    private Integer points;

    private Integer games;

    private Integer won;

    private Integer draw;

    private Integer lost;

    private Integer goalsScored;

    private Integer goalsAgainst;
}
