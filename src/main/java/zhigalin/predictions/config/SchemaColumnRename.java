package zhigalin.predictions.config;

import org.springframework.jdbc.core.JdbcTemplate;

public final class SchemaColumnRename {

    private SchemaColumnRename() {
    }

    public static void apply(JdbcTemplate jdbc) {
        rename(jdbc, "teams", "public_id", "id");

        rename(jdbc, "match", "public_id", "id");
        rename(jdbc, "match", "local_date_time", "starts_at");
        rename(jdbc, "match", "home_team_score", "home_score");
        rename(jdbc, "match", "away_team_score", "away_score");
        rename(jdbc, "match", "odd_home_team_total", "odd_home_total");
        rename(jdbc, "match", "odd_away_team_total", "odd_away_total");

        rename(jdbc, "cup_match", "public_id", "id");
        rename(jdbc, "cup_match", "local_date_time", "starts_at");
        rename(jdbc, "cup_match", "home_team_score", "home_score");
        rename(jdbc, "cup_match", "away_team_score", "away_score");
        rename(jdbc, "cup_match", "aet_home_score", "aet_home");
        rename(jdbc, "cup_match", "aet_away_score", "aet_away");
        rename(jdbc, "cup_match", "pen_home_score", "pen_home");
        rename(jdbc, "cup_match", "pen_away_score", "pen_away");

        rename(jdbc, "h2h", "local_date_time", "starts_at");
        rename(jdbc, "h2h", "home_team_score", "home_score");
        rename(jdbc, "h2h", "away_team_score", "away_score");

        rename(jdbc, "predict", "home_team_score", "home_score");
        rename(jdbc, "predict", "away_team_score", "away_score");

        rename(jdbc, "match_recommendation", "match_public_id", "match_id");
        rename(jdbc, "match_recommendation", "expected_home_goals", "expected_home");
        rename(jdbc, "match_recommendation", "expected_away_goals", "expected_away");

        rename(jdbc, "user_week_bonus_match", "match_public_id", "match_id");
        rename(jdbc, "notification_reminder_sent", "match_public_id", "match_id");

        rename(jdbc, "fixture_xg", "fixture_id", "match_id");
    }

    private static void rename(JdbcTemplate jdbc, String table, String from, String to) {
        jdbc.execute("""
                DO $$ BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = '%s' AND column_name = '%s'
                    ) AND NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = '%s' AND column_name = '%s'
                    ) THEN
                        ALTER TABLE %s RENAME COLUMN %s TO %s;
                    END IF;
                END $$
                """.formatted(table, from, table, to, table, from, to));
    }
}
