package zhigalin.predictions.config;

import org.springframework.jdbc.core.JdbcTemplate;

public final class RecommenderTableRename {

    private RecommenderTableRename() {
    }

    public static void apply(JdbcTemplate jdbcTemplate) {
        renameIfNeeded(jdbcTemplate, "footystats_team_stats", "recommender_team_stats");
        renameIfNeeded(jdbcTemplate, "footystats_league_snapshot", "recommender_league_snapshot");
        renameIfNeeded(jdbcTemplate, "api_football_fixture_xg", "fixture_xg");
    }

    private static void renameIfNeeded(JdbcTemplate jdbcTemplate, String from, String to) {
        jdbcTemplate.execute("""
                DO $$ BEGIN
                    IF to_regclass('public.%s') IS NOT NULL
                       AND to_regclass('public.%s') IS NULL THEN
                        ALTER TABLE %s RENAME TO %s;
                    END IF;
                END $$
                """.formatted(from, to, from, to));
    }
}
