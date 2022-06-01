package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

import javax.persistence.*;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
//@Audited
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "predict")
public class Prediction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "Predict_generator")
    @SequenceGenerator(sequenceName = "Predict_sequence", name = "Predict_generator", allocationSize = 1)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    //@Audited(targetAuditMode = NOT_AUDITED)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    //@Audited(targetAuditMode = NOT_AUDITED)
    private User user;

    //@Audited(targetAuditMode = NOT_AUDITED)
    private Integer homeTeamScore;

    //@Audited(targetAuditMode = NOT_AUDITED)
    private Integer awayTeamScore;

    //@NotAudited
    private Integer points;
}
