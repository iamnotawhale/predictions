package zhigalin.predictions.recommender;

import java.util.List;
import zhigalin.predictions.model.event.HeadToHead;

/**
 * Head-to-head rates from the perspective of the upcoming fixture's home/away sides.
 * {@code venue*} counts only meetings where {@code currentHomeId} was already at home.
 */
public record H2hStats(
        int overallGames,
        int currentHomeWinsOverall,
        int drawsOverall,
        int currentAwayWinsOverall,
        double avgGoalsCurrentHomeOverall,
        double avgGoalsCurrentAwayOverall,
        int venueGames,
        int currentHomeWinsAtVenue,
        int drawsAtVenue,
        int currentAwayWinsAtVenue,
        double avgGoalsCurrentHomeAtVenue,
        double avgGoalsCurrentAwayAtVenue
) {
    public static H2hStats from(List<HeadToHead> meetings, int currentHomeId, int currentAwayId) {
        if (meetings == null || meetings.isEmpty()) {
            return null;
        }
        int overall = 0;
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        int homeGoalsSum = 0;
        int awayGoalsSum = 0;

        int venue = 0;
        int venueHomeWins = 0;
        int venueDraws = 0;
        int venueAwayWins = 0;
        int venueHomeGoalsSum = 0;
        int venueAwayGoalsSum = 0;

        for (HeadToHead meeting : meetings) {
            if (meeting == null || meeting.getHomeTeamScore() < 0 || meeting.getAwayTeamScore() < 0) {
                continue;
            }
            boolean homeWasHome = meeting.getHomeTeamId() == currentHomeId
                    && meeting.getAwayTeamId() == currentAwayId;
            boolean homeWasAway = meeting.getHomeTeamId() == currentAwayId
                    && meeting.getAwayTeamId() == currentHomeId;
            if (!homeWasHome && !homeWasAway) {
                continue;
            }

            int goalsCurrentHome = homeWasHome ? meeting.getHomeTeamScore() : meeting.getAwayTeamScore();
            int goalsCurrentAway = homeWasHome ? meeting.getAwayTeamScore() : meeting.getHomeTeamScore();

            overall++;
            homeGoalsSum += goalsCurrentHome;
            awayGoalsSum += goalsCurrentAway;
            if (goalsCurrentHome > goalsCurrentAway) {
                homeWins++;
            } else if (goalsCurrentHome < goalsCurrentAway) {
                awayWins++;
            } else {
                draws++;
            }

            if (homeWasHome) {
                venue++;
                venueHomeGoalsSum += goalsCurrentHome;
                venueAwayGoalsSum += goalsCurrentAway;
                if (goalsCurrentHome > goalsCurrentAway) {
                    venueHomeWins++;
                } else if (goalsCurrentHome < goalsCurrentAway) {
                    venueAwayWins++;
                } else {
                    venueDraws++;
                }
            }
        }

        if (overall == 0) {
            return null;
        }
        return new H2hStats(
                overall,
                homeWins,
                draws,
                awayWins,
                homeGoalsSum / (double) overall,
                awayGoalsSum / (double) overall,
                venue,
                venueHomeWins,
                venueDraws,
                venueAwayWins,
                venue == 0 ? 0 : venueHomeGoalsSum / (double) venue,
                venue == 0 ? 0 : venueAwayGoalsSum / (double) venue
        );
    }

    public double homeWinRateOverall() {
        return smoothedRate(currentHomeWinsOverall, overallGames);
    }

    public double drawRateOverall() {
        return smoothedRate(drawsOverall, overallGames);
    }

    public double awayWinRateOverall() {
        return smoothedRate(currentAwayWinsOverall, overallGames);
    }

    public double homeWinRateVenue() {
        return venueGames == 0 ? homeWinRateOverall() : smoothedRate(currentHomeWinsAtVenue, venueGames);
    }

    public double drawRateVenue() {
        return venueGames == 0 ? drawRateOverall() : smoothedRate(drawsAtVenue, venueGames);
    }

    public double awayWinRateVenue() {
        return venueGames == 0 ? awayWinRateOverall() : smoothedRate(currentAwayWinsAtVenue, venueGames);
    }

    /** Blend overall + same-venue outcome rates; venue weighs more when sample is decent. */
    public double blendedHomeWinRate() {
        return blendVenue(homeWinRateOverall(), homeWinRateVenue());
    }

    public double blendedDrawRate() {
        return blendVenue(drawRateOverall(), drawRateVenue());
    }

    public double blendedAwayWinRate() {
        return blendVenue(awayWinRateOverall(), awayWinRateVenue());
    }

    private double blendVenue(double overall, double venue) {
        if (venueGames <= 0) {
            return overall;
        }
        if (venueGames >= 3) {
            return 0.45 * overall + 0.55 * venue;
        }
        if (venueGames == 2) {
            return 0.55 * overall + 0.45 * venue;
        }
        return 0.70 * overall + 0.30 * venue;
    }

    private static double smoothedRate(int count, int games) {
        if (games <= 0) {
            return 1.0 / 3.0;
        }
        return (count + 1.0) / (games + 3.0);
    }
}
