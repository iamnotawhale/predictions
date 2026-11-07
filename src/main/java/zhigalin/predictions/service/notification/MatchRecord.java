package zhigalin.predictions.service.notification;

import java.time.LocalDateTime;

import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;

public record MatchRecord(
        Integer homeTeamId,
        Integer awayTeamId,
        Integer weekId,
        LocalDateTime startsAt,
        int id,
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
                match.getStartsAt(),
                match.getId(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static MatchRecord fromCup(CupMatch match) {
        return new MatchRecord(
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                null,
                match.getStartsAt(),
                match.getId(),
                match.getCompetition(),
                match.getHomeEspnCode(),
                match.getAwayEspnCode(),
                match.getHomeLogoUrl(),
                match.getAwayLogoUrl()
        );
    }
}
