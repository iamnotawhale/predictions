package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("cupMatchesSchemaMigration")
public class CupMatchesSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger("server");

    public CupMatchesSchemaMigration(JdbcTemplate jdbcTemplate) {
        migrate(jdbcTemplate);
    }

    private void migrate(JdbcTemplate jdbc) {
        CupSchemaRename.apply(jdbc);
        RecommenderTableRename.apply(jdbc);
        SchemaColumnRename.apply(jdbc);
        try {
            jdbc.execute("ALTER TABLE match ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP");
            jdbc.execute("""
                    UPDATE match
                    SET finished_at = starts_at
                    WHERE status = 'ft' AND finished_at IS NULL AND starts_at IS NOT NULL
                    """);

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS cup_match (
                        id               INTEGER PRIMARY KEY,
                        competition      VARCHAR(64) NOT NULL,
                        espn_id          VARCHAR(32) NOT NULL,
                        home_team_id     INTEGER,
                        away_team_id     INTEGER,
                        home_name        VARCHAR(255),
                        away_name        VARCHAR(255),
                        home_logo_url    VARCHAR(512),
                        away_logo_url    VARCHAR(512),
                        home_espn_code   VARCHAR(16),
                        away_espn_code   VARCHAR(16),
                        home_score       INTEGER,
                        away_score       INTEGER,
                        result           VARCHAR(16),
                        status           VARCHAR(32),
                        starts_at        TIMESTAMP,
                        finished_at      TIMESTAMP,
                        live_score_message_id INTEGER,
                        CONSTRAINT unique_cup_match_espn UNIQUE (competition, espn_id)
                    )
                    """);

            jdbc.execute("ALTER TABLE cup_match ADD COLUMN IF NOT EXISTS aet_home INTEGER");
            jdbc.execute("ALTER TABLE cup_match ADD COLUMN IF NOT EXISTS aet_away INTEGER");
            jdbc.execute("ALTER TABLE cup_match ADD COLUMN IF NOT EXISTS pen_home INTEGER");
            jdbc.execute("ALTER TABLE cup_match ADD COLUMN IF NOT EXISTS pen_away INTEGER");

            jdbc.execute("ALTER TABLE predict ADD COLUMN IF NOT EXISTS cup_match_id INTEGER");
            jdbc.execute("ALTER TABLE predict ALTER COLUMN match_id DROP NOT NULL");
            jdbc.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM pg_constraint WHERE conname = 'unique_predict'
                        ) THEN
                            ALTER TABLE predict DROP CONSTRAINT unique_predict;
                        END IF;
                    END $$
                    """);
            jdbc.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS unique_predict_epl
                    ON predict (user_id, match_id)
                    WHERE match_id IS NOT NULL
                    """);
            jdbc.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS unique_predict_cup
                    ON predict (user_id, cup_match_id)
                    WHERE cup_match_id IS NOT NULL
                    """);
            Integer matchIdDupes = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT id FROM match
                        WHERE id IS NOT NULL
                        GROUP BY id HAVING COUNT(*) > 1
                    ) d
                    """, Integer.class);
            jdbc.execute("DROP INDEX IF EXISTS unique_match_id");
            if (matchIdDupes == null || matchIdDupes == 0) {
                jdbc.execute("""
                        CREATE UNIQUE INDEX IF NOT EXISTS unique_match_id
                        ON match (id) WHERE id IS NOT NULL
                        """);
            } else {
                log.warn("Skip unique_match_id: {} duplicate id values on match", matchIdDupes);
            }
            jdbc.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint WHERE conname = 'predict_match_xor_cup'
                        ) THEN
                            ALTER TABLE predict ADD CONSTRAINT predict_match_xor_cup
                            CHECK (
                                (match_id IS NOT NULL AND cup_match_id IS NULL)
                                OR (match_id IS NULL AND cup_match_id IS NOT NULL)
                            );
                        END IF;
                    END $$
                    """);

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS user_week_bonus_match (
                        user_id    INTEGER NOT NULL,
                        week_id    INTEGER NOT NULL,
                        match_id   INTEGER NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (user_id, week_id)
                    )
                    """);

            log.info("Cup matches / finished_at / week-bonus schema ensured");
        } catch (Exception e) {
            log.error("CupMatchesSchemaMigration failed: {}", e.getMessage());
            throw e;
        }
    }
}
