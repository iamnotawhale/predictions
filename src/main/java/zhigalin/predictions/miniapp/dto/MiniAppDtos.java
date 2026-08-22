package zhigalin.predictions.miniapp.dto;

import java.util.List;

public final class MiniAppDtos {

    private MiniAppDtos() {
    }

    public record ProfileResponse(
            String login,
            int currentWeekId,
            int season,
            String weekLabel
    ) {
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
            Double oddAway,
            String predictUntil,
            Long predictSecondsLeft
    ) {
    }

    public record LeaderboardEntry(String login, int points, Integer provisionalPoints, Integer liveDelta) {
    }

    public record LeaderboardResponse(List<LeaderboardEntry> entries, Integer weekId, String title, boolean liveActive) {
    }

    public record StandingItem(
            int place,
            String code,
            String name,
            String logo,
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

    public record FormItem(
            String outcome,
            int ownScore,
            int opponentScore,
            String opponentCode,
            String kickoff
    ) {
    }

    public record MatchNewsItem(
            String title,
            String url,
            String publishedAt
    ) {
    }

    public record MatchInsightsResponse(
            List<FormItem> homeForm,
            List<FormItem> awayForm,
            List<MatchNewsItem> news
    ) {
    }

    public record LineupPlayerItem(
            int number,
            String name,
            String position
    ) {
    }

    public record MatchEventItem(
            String minute,
            String text,
            String type,
            Double fieldX,
            Double fieldY,
            Double field2X,
            Double field2Y,
            Double goalPositionY,
            String teamName,
            String shortText,
            String playerName
    ) {
    }

    public record MatchStatItem(
            String key,
            String label,
            String homeValue,
            String awayValue
    ) {
    }

    public record LiveMatchDetailsResponse(
            boolean live,
            List<LineupPlayerItem> homeLineup,
            List<LineupPlayerItem> awayLineup,
            List<MatchEventItem> events,
            List<MatchStatItem> matchStats,
            String homeColor,
            String awayColor,
            Integer homeScore,
            Integer awayScore,
            String status
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

    public record TodayMatchesResponse(List<MatchItem> matches, boolean hasLive) {
    }

    public record ChartSeries(String login, String label, List<Integer> points) {
    }

    public record PointsChartResponse(List<Integer> weeks, List<ChartSeries> series) {
    }

    public record CrowdScoreBucket(String score, int count, int percent) {
    }

    public record CrowdMeterResponse(
            int matchPublicId,
            int totalPredictions,
            int homeWinPct,
            int drawPct,
            int awayWinPct,
            List<CrowdScoreBucket> topScores
    ) {
    }

    public record WeekReviewItem(
            int publicId,
            String homeCode,
            String awayCode,
            String status,
            Integer homeScore,
            Integer awayScore,
            Integer predictHome,
            Integer predictAway,
            Integer points,
            boolean hasPrediction
    ) {
    }

    public record WeekReviewResponse(
            int weekId,
            int totalPoints,
            List<WeekReviewItem> items
    ) {
    }
}
