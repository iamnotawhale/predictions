package zhigalin.predictions.service.notification;

import java.time.LocalDateTime;

import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.event.Match;

public record MatchRecord(
        Integer homeTeamId,
        Integer awayTeamId,
        Integer weekId,
        LocalDateTime localDateTime,
        int publicId,
        String competition,
        String homeCode,
        String awayCode,
        String homeLogoUrl,
        String awayLogoUrl
) {
    public boolean isCup() {
        return competition != null && !competition.isBlank();
    }

    public static MatchRecord fromEpl(Match match) {
        return new MatchRecord(
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getWeekId(),
                match.getLocalDateTime(),
                match.getPublicId(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static MatchRecord fromCup(BonusMatch match) {
        return new MatchRecord(
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                null,
                match.getLocalDateTime(),
                match.getPublicId(),
                match.getCompetition(),
                match.getHomeEspnCode(),
                match.getAwayEspnCode(),
                match.getHomeLogoUrl(),
                match.getAwayLogoUrl()
        );
    }
}
