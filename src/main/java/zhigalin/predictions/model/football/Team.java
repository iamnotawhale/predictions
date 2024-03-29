package zhigalin.predictions.model.football;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;

@Builder
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
    @SequenceGenerator(sequenceName = "Team_sequence", name = "Team_generator", allocationSize = 1)
    private Long id;
    private Long publicId;
    private String name;
    private String code;
    private String logo;
}
