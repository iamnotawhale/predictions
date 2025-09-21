package zhigalin.predictions.service.notification;

import java.time.LocalDateTime;

public record MatchRecord(int homeTeamId, int awayTeamId, int weekId, LocalDateTime localDateTime) {}
