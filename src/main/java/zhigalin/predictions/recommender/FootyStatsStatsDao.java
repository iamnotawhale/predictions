package zhigalin.predictions.recommender;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;

@Repository
public class FootyStatsStatsDao {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FootyStatsStatsDao(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void replaceTeamStats(int weekId, List<FootyStatsTeamSnapshot> teams) {
        jdbcTemplate.update("DELETE FROM footystats_team_stats WHERE week_id = ?", weekId);
        for (FootyStatsTeamSnapshot team : teams) {
            String extendedJson = serializeExtended(team.extendedOrEmpty());
            jdbcTemplate.update("""
                            INSERT INTO footystats_team_stats (
                                week_id, team_code,
                                scored_overall, scored_home, scored_away,
                                conceded_overall, conceded_home, conceded_away,
                                xg_overall, xg_home, xg_away,
                                xga_overall, xgd_overall, extended_json, fetched_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    weekId,
                    team.teamCode(),
                    team.scoredOverall(),
                    team.scoredHome(),
                    team.scoredAway(),
                    team.concededOverall(),
                    team.concededHome(),
                    team.concededAway(),
                    team.xgOverall(),
                    team.xgHome(),
                    team.xgAway(),
                    team.xgaOverall(),
                    team.xgdOverall(),
                    extendedJson,
                    Timestamp.from(team.fetchedAt()));
        }
    }

    private String serializeExtended(FootyStatsExtendedMetrics extended) {
        try {
            return objectMapper.writeValueAsString(extended);
        } catch (Exception e) {
            return "{}";
        }
    }

    private FootyStatsExtendedMetrics deserializeExtended(String json) {
        if (json == null || json.isBlank()) {
            return FootyStatsExtendedMetrics.empty();
        }
        try {
            return objectMapper.readValue(json, FootyStatsExtendedMetrics.class);
        } catch (Exception e) {
            return FootyStatsExtendedMetrics.empty();
        }
    }

    public void saveLeagueSnapshot(FootyStatsLeagueSnapshot snapshot) {
        jdbcTemplate.update("""
                        INSERT INTO footystats_league_snapshot (
                            week_id, avg_home_scored, avg_away_scored, avg_home_conceded, avg_away_conceded, fetched_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (week_id) DO UPDATE SET
                            avg_home_scored = EXCLUDED.avg_home_scored,
                            avg_away_scored = EXCLUDED.avg_away_scored,
                            avg_home_conceded = EXCLUDED.avg_home_conceded,
                            avg_away_conceded = EXCLUDED.avg_away_conceded,
                            fetched_at = EXCLUDED.fetched_at
                        """,
                snapshot.weekId(),
                snapshot.avgHomeScored(),
                snapshot.avgAwayScored(),
                snapshot.avgHomeConceded(),
                snapshot.avgAwayConceded(),
                Timestamp.from(snapshot.fetchedAt()));
    }

    public Optional<FootyStatsLeagueSnapshot> findLeagueSnapshot(int weekId) {
        List<FootyStatsLeagueSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM footystats_league_snapshot WHERE week_id = ?",
                leagueMapper(),
                weekId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<FootyStatsTeamSnapshot> findTeamStats(int weekId) {
        return jdbcTemplate.query(
                "SELECT * FROM footystats_team_stats WHERE week_id = ?",
                teamMapper(),
                weekId
        );
    }

    public Optional<FootyStatsTeamSnapshot> findTeamStats(int weekId, String teamCode) {
        List<FootyStatsTeamSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM footystats_team_stats WHERE week_id = ? AND team_code = ?",
                teamMapper(),
                weekId,
                teamCode
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void saveRecommendation(MatchRecommendationSnapshot recommendation) {
        String explanationJson;
        try {
            explanationJson = objectMapper.writeValueAsString(recommendation.explanationLines());
        } catch (Exception e) {
            explanationJson = "[]";
        }
        jdbcTemplate.update("""
                        INSERT INTO match_recommendation (
                            match_public_id, week_id,
                            recommended_home, recommended_away,
                            expected_home_goals, expected_away_goals,
                            score_probability, explanation_json, computed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (match_public_id) DO UPDATE SET
                            week_id = EXCLUDED.week_id,
                            recommended_home = EXCLUDED.recommended_home,
                            recommended_away = EXCLUDED.recommended_away,
                            expected_home_goals = EXCLUDED.expected_home_goals,
                            expected_away_goals = EXCLUDED.expected_away_goals,
                            score_probability = EXCLUDED.score_probability,
                            explanation_json = EXCLUDED.explanation_json,
                            computed_at = EXCLUDED.computed_at
                        """,
                recommendation.matchPublicId(),
                recommendation.weekId(),
                recommendation.recommendedHome(),
                recommendation.recommendedAway(),
                recommendation.expectedHomeGoals(),
                recommendation.expectedAwayGoals(),
                recommendation.scoreProbability(),
                explanationJson,
                Timestamp.from(recommendation.computedAt()));
    }

    public Optional<MatchRecommendationSnapshot> findRecommendation(int matchPublicId) {
        List<MatchRecommendationSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM match_recommendation WHERE match_public_id = ?",
                recommendationMapper(),
                matchPublicId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void deleteRecommendationsForWeek(int weekId) {
        jdbcTemplate.update("DELETE FROM match_recommendation WHERE week_id = ?", weekId);
    }

    public boolean hasRecommendationsForWeek(int weekId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_recommendation WHERE week_id = ?",
                Integer.class,
                weekId
        );
        return count != null && count > 0;
    }

    private RowMapper<FootyStatsTeamSnapshot> teamMapper() {
        return (rs, rowNum) -> new FootyStatsTeamSnapshot(
                rs.getString("team_code"),
                rs.getDouble("scored_overall"),
                rs.getDouble("scored_home"),
                rs.getDouble("scored_away"),
                rs.getDouble("conceded_overall"),
                rs.getDouble("conceded_home"),
                rs.getDouble("conceded_away"),
                getNullableDouble(rs, "xg_overall"),
                getNullableDouble(rs, "xga_overall"),
                getNullableDouble(rs, "xgd_overall"),
                getNullableDouble(rs, "xg_home"),
                getNullableDouble(rs, "xg_away"),
                deserializeExtended(rs.getString("extended_json")),
                rs.getTimestamp("fetched_at").toInstant()
        );
    }

    private RowMapper<FootyStatsLeagueSnapshot> leagueMapper() {
        return (rs, rowNum) -> new FootyStatsLeagueSnapshot(
                rs.getInt("week_id"),
                rs.getDouble("avg_home_scored"),
                rs.getDouble("avg_away_scored"),
                rs.getDouble("avg_home_conceded"),
                rs.getDouble("avg_away_conceded"),
                rs.getTimestamp("fetched_at").toInstant()
        );
    }

    private RowMapper<MatchRecommendationSnapshot> recommendationMapper() {
        return (rs, rowNum) -> {
            List<String> lines;
            try {
                lines = objectMapper.readValue(rs.getString("explanation_json"), new TypeReference<>() {
                });
            } catch (Exception e) {
                lines = List.of();
            }
            return new MatchRecommendationSnapshot(
                    rs.getInt("match_public_id"),
                    rs.getInt("week_id"),
                    rs.getInt("recommended_home"),
                    rs.getInt("recommended_away"),
                    rs.getDouble("expected_home_goals"),
                    rs.getDouble("expected_away_goals"),
                    rs.getDouble("score_probability"),
                    lines,
                    buildSummary(rs.getInt("recommended_home"), rs.getInt("recommended_away")),
                    rs.getTimestamp("computed_at").toInstant()
            );
        };
    }

    private static String buildSummary(int home, int away) {
        return home + ":" + away;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
