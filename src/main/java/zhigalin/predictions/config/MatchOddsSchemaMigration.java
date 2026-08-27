package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures match odds columns exist on deployments where tablesInit.sql
 * was applied before ALTER statements were added.
 */
@Component("matchOddsSchemaMigration")
public class MatchOddsSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final JdbcTemplate jdbcTemplate;

    public MatchOddsSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        migrate();
    }

    private void migrate() {
        try {
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_home NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_draw NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_away NUMERIC(6, 2)");
            log.info("Match odds columns ensured");
        } catch (Exception e) {
            log.error("Failed to migrate match odds columns: {}", e.getMessage());
            throw e;
        }
    }
}
