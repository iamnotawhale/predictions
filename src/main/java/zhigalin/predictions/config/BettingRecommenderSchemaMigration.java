package zhigalin.predictions.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("bettingRecommenderSchemaMigration")
public class BettingRecommenderSchemaMigration {

    public BettingRecommenderSchemaMigration(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS betting_recommender_enabled BOOLEAN DEFAULT FALSE
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS footystats_team_stats (
                    week_id INTEGER NOT NULL,
                    team_code VARCHAR(8) NOT NULL,
                    scored_overall NUMERIC(6, 2),
                    scored_home NUMERIC(6, 2),
                    scored_away NUMERIC(6, 2),
                    conceded_overall NUMERIC(6, 2),
                    conceded_home NUMERIC(6, 2),
                    conceded_away NUMERIC(6, 2),
                    xg_overall NUMERIC(6, 2),
                    xg_home NUMERIC(6, 2),
                    xg_away NUMERIC(6, 2),
                    fetched_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (week_id, team_code)
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE footystats_team_stats
                    ADD COLUMN IF NOT EXISTS xga_overall NUMERIC(6, 2)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE footystats_team_stats
                    ADD COLUMN IF NOT EXISTS xgd_overall NUMERIC(6, 2)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE footystats_team_stats
                    ADD COLUMN IF NOT EXISTS extended_json TEXT
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS footystats_league_snapshot (
                    week_id INTEGER PRIMARY KEY,
                    avg_home_scored NUMERIC(6, 2),
                    avg_away_scored NUMERIC(6, 2),
                    avg_home_conceded NUMERIC(6, 2),
                    avg_away_conceded NUMERIC(6, 2),
                    fetched_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS match_recommendation (
                    match_public_id INTEGER PRIMARY KEY,
                    week_id INTEGER NOT NULL,
                    recommended_home INTEGER NOT NULL,
                    recommended_away INTEGER NOT NULL,
                    expected_home_goals NUMERIC(6, 3) NOT NULL,
                    expected_away_goals NUMERIC(6, 3) NOT NULL,
                    score_probability NUMERIC(8, 6),
                    explanation_json TEXT NOT NULL,
                    computed_at TIMESTAMP NOT NULL
                )
                """);
    }
}
