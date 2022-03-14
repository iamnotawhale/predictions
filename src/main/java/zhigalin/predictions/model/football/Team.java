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
@Table(name = "teams")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Team_generator")
    @SequenceGenerator(sequenceName = "Team_sequence", name = "Team_generator")
    private Long id;

    private String teamName;

    private String code;
}
