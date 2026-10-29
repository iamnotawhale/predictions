package zhigalin.predictions.miniapp;

import java.time.LocalDateTime;
import java.util.Set;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;

/** Status-string and prediction-window rules shared by EPL and cup matches. */
final class MatchStatus {
    private static final Set<String> CLOSED = Set.of("ft", "aet", "pen", "canc", "abd", "awrd", "wo");
    private static final Set<String> NOT_STARTED = Set.of("ns", "pst", "tbd");
    private static final Set<String> NOT_LIVE = Set.of("ns", "ft", "aet", "pen", "pst", "canc", "abd", "awrd", "wo");
    /** Predictions stay open this long after kickoff. */
    private static final int PREDICT_GRACE_MINUTES = 5;

    private MatchStatus() {
    }

    static boolean isFinished(String status) {
        return status != null && CLOSED.contains(status.toLowerCase());
    }

    static boolean isNotStarted(String status) {
        return status == null || status.isBlank() || NOT_STARTED.contains(status.toLowerCase());
    }

    static boolean isLive(String status) {
        return status != null && !status.isBlank() && !NOT_LIVE.contains(status.toLowerCase());
    }

    /** In-play EPL match that feeds provisional week points (same gate as computeCurrentWeekProvisionalPoints). */
    static boolean isLiveLike(Match match) {
        return match != null && isLiveLike(match.getStatus(), match.getHomeTeamScore(), match.getAwayTeamScore());
    }

    static boolean isLiveLike(String status, Integer homeScore, Integer awayScore) {
        return isLive(status)
               || (homeScore != null && awayScore != null
                   && !isFinished(status) && !"ns".equalsIgnoreCase(String.valueOf(status)));
    }

    static boolean canPredict(Match match) {
        return match != null && canPredictAt(match.getStatus(), match.getLocalDateTime(), LocalDateTime.now());
    }

    static boolean canPredictBonus(CupMatch match) {
        return match != null && canPredictAt(match.getStatus(), match.getLocalDateTime(), LocalDateTime.now());
    }

    static boolean canPredictAt(String status, LocalDateTime kickoff, LocalDateTime now) {
        if (kickoff == null || isFinished(status)) {
            return false;
        }
        return now.isBefore(kickoff.plusMinutes(PREDICT_GRACE_MINUTES));
    }
}
