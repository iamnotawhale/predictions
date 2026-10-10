package zhigalin.predictions.repository.notification;

import java.util.List;
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

    /** Latest stored Telegram message id for a prior reminder on this user+match (any window). */
    public Integer findLatestReminderTelegramMessageId(int userId, int matchPublicId) {
        try {
            String sql = """
                    SELECT telegram_message_id
                    FROM notification_reminder_sent
                    WHERE user_id = :userId
                      AND match_public_id = :matchPublicId
                      AND telegram_message_id IS NOT NULL
                    ORDER BY sent_at DESC
                    LIMIT 1
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("matchPublicId", matchPublicId);
            List<Integer> ids = namedParameterJdbcTemplate.query(
                    sql,
                    params,
                    (rs, rowNum) -> {
                        int v = rs.getInt("telegram_message_id");
                        return rs.wasNull() ? null : v;
                    }
            );
            return ids.isEmpty() ? null : ids.getFirst();
        } catch (Exception e) {
            serverLogger.warn("findLatestReminderTelegramMessageId: {}", e.getMessage());
            return null;
        }
    }

    public void saveReminderTelegramMessageId(int userId, int matchPublicId, int reminderMinutes, Integer messageId) {
        if (messageId == null) {
            return;
        }
        try {
            String sql = """
                    UPDATE notification_reminder_sent
                    SET telegram_message_id = :messageId
                    WHERE user_id = :userId
                      AND match_public_id = :matchPublicId
                      AND reminder_minutes = :reminderMinutes
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("messageId", messageId)
                    .addValue("userId", userId)
                    .addValue("matchPublicId", matchPublicId)
                    .addValue("reminderMinutes", reminderMinutes);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            serverLogger.warn("saveReminderTelegramMessageId: {}", e.getMessage());
        }
    }

    /** A sent reminder whose Telegram message should disappear once predictions are closed. */
    public record ExpiredReminder(int userId, int matchPublicId, int reminderMinutes, int messageId) {
    }

    /** Reminders still visible in chat for matches whose prediction window (kickoff + graceMinutes) is over. */
    public List<ExpiredReminder> findExpiredReminders(int graceMinutes) {
        try {
            String sql = """
                    SELECT r.user_id, r.match_public_id, r.reminder_minutes, r.telegram_message_id
                    FROM notification_reminder_sent r
                    JOIN match m ON m.public_id = r.match_public_id
                    WHERE r.telegram_message_id IS NOT NULL
                      AND r.message_deleted_at IS NULL
                      AND m.local_date_time + make_interval(mins => :grace) < NOW()
                    """;
            return namedParameterJdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("grace", graceMinutes),
                    (rs, rowNum) -> new ExpiredReminder(
                            rs.getInt("user_id"),
                            rs.getInt("match_public_id"),
                            rs.getInt("reminder_minutes"),
                            rs.getInt("telegram_message_id"))
            );
        } catch (Exception e) {
            serverLogger.warn("findExpiredReminders: {}", e.getMessage());
            return List.of();
        }
    }

    public void markReminderMessageDeleted(int userId, int matchPublicId, int reminderMinutes) {
        try {
            namedParameterJdbcTemplate.update("""
                    UPDATE notification_reminder_sent
                    SET message_deleted_at = NOW()
                    WHERE user_id = :userId AND match_public_id = :matchPublicId
                      AND reminder_minutes = :reminderMinutes
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("matchPublicId", matchPublicId)
                    .addValue("reminderMinutes", reminderMinutes));
        } catch (Exception e) {
            serverLogger.warn("markReminderMessageDeleted: {}", e.getMessage());
        }
    }
}
