package zhigalin.predictions.model.predict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

import javax.persistence.*;

@Builder
@Data
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

    public void setPoints() {
        Integer realHomeScore = this.match.getHomeTeamScore();
        Integer realAwayScore = this.match.getAwayTeamScore();
        Integer predictHomeScore = this.getHomeTeamScore();
        Integer predictAwayScore = this.getAwayTeamScore();
        this.points = (long) (realHomeScore == null || realAwayScore == null ? 0
                : realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore) ? 5
                : realHomeScore - realAwayScore == predictHomeScore - predictAwayScore ? 3
                : realHomeScore > realAwayScore && predictHomeScore > predictAwayScore ? 1
                : realHomeScore < realAwayScore && predictHomeScore < predictAwayScore ? 1 : -1);
    }
}
