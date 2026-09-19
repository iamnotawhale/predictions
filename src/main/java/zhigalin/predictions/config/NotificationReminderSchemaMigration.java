package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("notificationReminderSchemaMigration")
public class NotificationReminderSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger("server");

    public NotificationReminderSchemaMigration(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE notification_reminder_sent
                    ADD COLUMN IF NOT EXISTS telegram_message_id integer
                    """);
            log.info("notification_reminder_sent.telegram_message_id ensured");
        } catch (Exception e) {
            log.error("Failed to migrate notification_reminder_sent: {}", e.getMessage());
            throw e;
        }
    }
}
