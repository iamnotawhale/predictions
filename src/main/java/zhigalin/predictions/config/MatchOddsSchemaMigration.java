package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("matchOddsSchemaMigration")
public class MatchOddsSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger("server");

    public MatchOddsSchemaMigration(JdbcTemplate jdbcTemplate) {
        SchemaColumnRename.apply(jdbcTemplate);
        try {
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_home NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_draw NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_away NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_over_under NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_home_total NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS odd_away_total NUMERIC(6, 2)");
            log.info("Match odds columns ensured");
        } catch (Exception e) {
            log.error("Failed to migrate match odds columns: {}", e.getMessage());
            throw e;
        }
    }
}
