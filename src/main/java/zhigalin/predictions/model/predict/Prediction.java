package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

import javax.persistence.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "predict")
public class Prediction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "Predict_generator")
    @SequenceGenerator(sequenceName = "Predict_sequence", name = "Predict_generator", allocationSize = 1)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", referencedColumnName = "id")
    private Match match;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private Long points;
}
