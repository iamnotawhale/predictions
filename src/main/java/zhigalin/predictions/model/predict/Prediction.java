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
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "predict")
public class Prediction {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Predict_generator")
    @SequenceGenerator(sequenceName = "Predict_sequence", name = "Predict_generator")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Integer homeTeamScore;

    private Integer awayTeamScore;

    /*public void setPoints(Integer points) {
        this.points = toPredict();
    }

    private Integer points;

    private Integer toPredict() {

        Integer realHomeScore = match.getHomeTeamScore();
        Integer realAwayScore = match.getAwayTeamScore();
        Integer predictHomeScore = getHomeTeamScore();
        Integer predictAwayScore = getAwayTeamScore();

        if (realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore)) {
            return 5;
        } else if (realHomeScore - realAwayScore == predictHomeScore - predictAwayScore) {
            return 3;
        } else if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
            return 1;
        } else if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
            return 1;
        } else {
            return 0;
        }
    }*/
}
