package zhigalin.predictions.dto.predict;

import lombok.Builder;
import lombok.Value;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

@Value
@Builder
public class PredictionDto {

    Long id;

    Match match;

    User user;

    Integer homeTeamScore;

    Integer awayTeamScore;

    public Integer getPoints() {
        Integer realHomeScore = this.match.getHomeTeamScore();
        Integer realAwayScore = this.match.getAwayTeamScore();
        Integer predictHomeScore = this.getHomeTeamScore();
        Integer predictAwayScore = this.getAwayTeamScore();

        return realHomeScore == null || realAwayScore == null ? 0
                : realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore) ? 5
                : realHomeScore - realAwayScore == predictHomeScore - predictAwayScore ? 3
                : realHomeScore > realAwayScore && predictHomeScore > predictAwayScore ? 1
                : realHomeScore < realAwayScore && predictHomeScore < predictAwayScore ? 1 : -1;
    }
}
