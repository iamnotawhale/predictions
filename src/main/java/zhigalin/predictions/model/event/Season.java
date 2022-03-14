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
@Table(name = "seasons")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Season_generator")
    @SequenceGenerator(sequenceName = "Season_sequence", name = "Season_generator")
    private Long id;
    private String seasonName;
}
