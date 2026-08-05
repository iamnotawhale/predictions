package zhigalin.predictions.miniapp.dto;

import java.util.List;

public final class MiniAppDtos {

    private MiniAppDtos() {
    }

    public record ProfileResponse(String login, int currentWeekId) {
    }

    public record WeekItem(int id, boolean hasPredictions) {
    }

    public record MatchItem(
            int publicId,
            int weekId,
            String homeCode,
            String homeName,
            String homeLogo,
            String awayCode,
            String awayName,
            String awayLogo,
            String status,
            Integer homeScore,
            Integer awayScore,
            String kickoff,
            boolean canPredict,
            boolean hasPrediction,
            Integer predictHome,
            Integer predictAway,
            Integer points,
            Double oddHome,
            Double oddDraw,
            Double oddAway
    ) {
    }

    public record LeaderboardEntry(String login, int points) {
    }

    public record LeaderboardResponse(List<LeaderboardEntry> entries, Integer weekId, String title) {
    }

    public record StandingItem(
            int place,
            String code,
            String name,
            int played,
            int won,
            int drawn,
            int lost,
            int goalsFor,
            int goalsAgainst,
            int points
    ) {
    }

    public record TeamMatchItem(
            int publicId,
            int weekId,
            String homeCode,
            String homeName,
            String homeLogo,
            String awayCode,
            String awayName,
            String awayLogo,
            String status,
            Integer homeScore,
            Integer awayScore,
            String kickoff
    ) {
    }

    public record TeamMatchesResponse(
            String teamCode,
            String teamName,
            List<TeamMatchItem> lastMatches,
            List<TeamMatchItem> upcomingMatches
    ) {
    }

    public record H2hItem(
            String leagueName,
            String kickoff,
            String homeCode,
            String awayCode,
            Integer homeScore,
            Integer awayScore
    ) {
    }

    public record ClientLogRequest(
            String level,
            String event,
            String details,
            String href,
            String userAgent
    ) {
    }

    public record PredictRequest(String homeCode, String awayCode, int homeScore, int awayScore) {
    }

    public record ActionResponse(boolean ok, String message, Integer predictHome, Integer predictAway) {
        public ActionResponse(boolean ok, String message) {
            this(ok, message, null, null);
        }
    }

    public record TodayMatchesResponse(List<MatchItem> matches) {
    }

    public record ChartSeries(String login, String label, List<Integer> points) {
    }

    public record PointsChartResponse(List<Integer> weeks, List<ChartSeries> series) {
    }
}
