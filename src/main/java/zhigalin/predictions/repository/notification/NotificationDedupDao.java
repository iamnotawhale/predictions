package zhigalin.predictions.repository.notification;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.panic.PanicSender;

@Repository
public class NotificationDedupDao {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final PanicSender panicSender;
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    public NotificationDedupDao(DataSource dataSource, PanicSender panicSender) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public boolean tryMarkWeeklyResultsSent(int weekId) {
        try {
            String sql = """
                    INSERT INTO notification_weekly_results_sent (week_id, sent_at)
                    VALUES (:weekId, NOW())
                    ON CONFLICT (week_id) DO NOTHING
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("weekId", weekId);
            return namedParameterJdbcTemplate.update(sql, params) > 0;
        } catch (Exception e) {
            panicSender.sendPanic("Error while marking weekly results sent", e);
            serverLogger.error(e.getMessage());
            return false;
        }
    }

    public boolean tryMarkReminderSent(int userId, int matchPublicId, int reminderMinutes) {
        try {
            String sql = """
                    INSERT INTO notification_reminder_sent (user_id, match_public_id, reminder_minutes, sent_at)
                    VALUES (:userId, :matchPublicId, :reminderMinutes, NOW())
                    ON CONFLICT (user_id, match_public_id, reminder_minutes) DO NOTHING
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            params.addValue("matchPublicId", matchPublicId);
            params.addValue("reminderMinutes", reminderMinutes);
            return namedParameterJdbcTemplate.update(sql, params) > 0;
        } catch (Exception e) {
            panicSender.sendPanic("Error while marking reminder sent", e);
            serverLogger.error(e.getMessage());
            return false;
        }
    }
}
