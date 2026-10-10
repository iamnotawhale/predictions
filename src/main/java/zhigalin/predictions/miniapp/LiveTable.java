package zhigalin.predictions.miniapp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zhigalin.predictions.model.event.Match;

/** Helpers for the live standings view: which teams are playing and how the table moves. */
final class LiveTable {
    private LiveTable() {
    }

    /** In-play matches with a score, keyed by both participating team ids. */
    static Map<Integer, Match> liveMatchByTeam(List<Match> onlineMatches) {
        Map<Integer, Match> byTeam = new LinkedHashMap<>();
        for (Match match : onlineMatches) {
            if (!MatchStatus.isLive(match.getStatus())) {
                continue;
            }
            if (match.getHomeTeamScore() == null || match.getAwayTeamScore() == null) {
                continue;
            }
            byTeam.put(match.getHomeTeamId(), match);
            byTeam.put(match.getAwayTeamId(), match);
        }
        return byTeam;
    }

    /** Positive = climbed vs. the finished-only table; zero when nothing is live. */
    static int placeDelta(boolean hasLive, int previousPlace, int currentPlace) {
        return hasLive ? previousPlace - currentPlace : 0;
    }

    static String scoreForTeam(Match match, int teamId) {
        int[] s = scoreFor(match, teamId);
        return s[0] + ":" + s[1];
    }

    static String resultForTeam(Match match, int teamId) {
        int[] s = scoreFor(match, teamId);
        return s[0] > s[1] ? "W" : s[0] < s[1] ? "L" : "D";
    }

    private static int[] scoreFor(Match match, int teamId) {
        int home = match.getHomeTeamScore() == null ? 0 : match.getHomeTeamScore();
        int away = match.getAwayTeamScore() == null ? 0 : match.getAwayTeamScore();
        return match.getHomeTeamId() == teamId ? new int[] {home, away} : new int[] {away, home};
    }
}
