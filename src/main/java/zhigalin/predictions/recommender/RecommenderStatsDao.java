package zhigalin.predictions.recommender;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.recommender.model.ExplanationBreakdown;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.LeagueAveragesSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;

@Repository
public class RecommenderStatsDao {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommenderStatsDao(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void replaceTeamStats(int weekId, List<TeamStatsSnapshot> teams) {
        jdbcTemplate.update("DELETE FROM recommender_team_stats WHERE week_id = ?", weekId);
        if (teams == null || teams.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO recommender_team_stats (
                            week_id, team_code,
                            scored_overall, scored_home, scored_away,
                            conceded_overall, conceded_home, conceded_away,
                            xg_overall, xg_home, xg_away,
                            xga_overall, xgd_overall, extended_json, fetched_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                teams,
                teams.size(),
                (ps, team) -> {
                    ps.setInt(1, weekId);
                    ps.setString(2, team.teamCode());
                    ps.setDouble(3, team.scoredOverall());
                    ps.setDouble(4, team.scoredHome());
                    ps.setDouble(5, team.scoredAway());
                    ps.setDouble(6, team.concededOverall());
                    ps.setDouble(7, team.concededHome());
                    ps.setDouble(8, team.concededAway());
                    setNullableDouble(ps, 9, team.xgOverall());
                    setNullableDouble(ps, 10, team.xgHome());
                    setNullableDouble(ps, 11, team.xgAway());
                    setNullableDouble(ps, 12, team.xgaOverall());
                    setNullableDouble(ps, 13, team.xgdOverall());
                    ps.setString(14, serializeExtended(team.extendedOrEmpty()));
                    ps.setTimestamp(15, Timestamp.from(team.fetchedAt()));
                });
    }

    private String serializeExtended(ExtendedTeamMetrics extended) {
        try {
            return objectMapper.writeValueAsString(extended);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ExtendedTeamMetrics deserializeExtended(String json) {
        if (json == null || json.isBlank()) {
            return ExtendedTeamMetrics.empty();
        }
        try {
            return objectMapper.readValue(json, ExtendedTeamMetrics.class);
        } catch (Exception e) {
            return ExtendedTeamMetrics.empty();
        }
    }

    public void saveLeagueSnapshot(LeagueAveragesSnapshot snapshot) {
        jdbcTemplate.update("""
                        INSERT INTO recommender_league_snapshot (
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

    public Optional<LeagueAveragesSnapshot> findLeagueSnapshot(int weekId) {
        List<LeagueAveragesSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM recommender_league_snapshot WHERE week_id = ?",
                leagueMapper(),
                weekId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<TeamStatsSnapshot> findTeamStats(int weekId) {
        return jdbcTemplate.query(
                "SELECT * FROM recommender_team_stats WHERE week_id = ?",
                teamMapper(),
                weekId
        );
    }

    public Optional<TeamStatsSnapshot> findTeamStats(int weekId, String teamCode) {
        List<TeamStatsSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM recommender_team_stats WHERE week_id = ? AND team_code = ?",
                teamMapper(),
                weekId,
                teamCode
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<TeamStatsSnapshot> findLatestTeamStats(String teamCode) {
        if (teamCode == null || teamCode.isBlank()) {
            return Optional.empty();
        }
        List<TeamStatsSnapshot> rows = jdbcTemplate.query(
                """
                        SELECT * FROM recommender_team_stats
                        WHERE team_code = ?
                        ORDER BY week_id DESC
                        LIMIT 1
                        """,
                teamMapper(),
                teamCode
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void saveRecommendations(List<MatchRecommendationSnapshot> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO match_recommendation (
                            match_id, week_id,
                            recommended_home, recommended_away,
                            expected_home, expected_away,
                            score_probability, explanation_json, computed_at,
                            shadow_home, shadow_away
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (match_id) DO UPDATE SET
                            week_id = EXCLUDED.week_id,
                            recommended_home = EXCLUDED.recommended_home,
                            recommended_away = EXCLUDED.recommended_away,
                            expected_home = EXCLUDED.expected_home,
                            expected_away = EXCLUDED.expected_away,
                            score_probability = EXCLUDED.score_probability,
                            explanation_json = EXCLUDED.explanation_json,
                            computed_at = EXCLUDED.computed_at,
                            shadow_home = EXCLUDED.shadow_home,
                            shadow_away = EXCLUDED.shadow_away
                        """,
                recommendations,
                recommendations.size(),
                (ps, recommendation) -> {
                    String explanationJson;
                    try {
                        ExplanationBreakdown explanation = recommendation.explanation() != null
                                ? recommendation.explanation()
                                : ExplanationBreakdown.empty();
                        explanationJson = objectMapper.writeValueAsString(explanation);
                    } catch (Exception e) {
                        explanationJson = "{\"rows\":[],\"notes\":[]}";
                    }
                    ps.setInt(1, recommendation.matchId());
                    ps.setInt(2, recommendation.weekId());
                    ps.setInt(3, recommendation.recommendedHome());
                    ps.setInt(4, recommendation.recommendedAway());
                    ps.setDouble(5, recommendation.expectedHome());
                    ps.setDouble(6, recommendation.expectedAway());
                    ps.setDouble(7, recommendation.scoreProbability());
                    ps.setString(8, explanationJson);
                    ps.setTimestamp(9, Timestamp.from(recommendation.computedAt()));
                    ps.setObject(10, recommendation.shadowHome(), java.sql.Types.INTEGER);
                    ps.setObject(11, recommendation.shadowAway(), java.sql.Types.INTEGER);
                });
    }

    public Optional<MatchRecommendationSnapshot> findRecommendation(int matchId) {
        List<MatchRecommendationSnapshot> rows = jdbcTemplate.query(
                "SELECT * FROM match_recommendation WHERE match_id = ?",
                recommendationMapper(),
                matchId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * One-shot freeze: copy live recommended score into kickoff_* if not already frozen.
     *
     * @return true if this call performed the freeze
     */
    public boolean freezeAtKickoff(int matchId) {
        int updated = jdbcTemplate.update("""
                        UPDATE match_recommendation
                        SET kickoff_home = recommended_home,
                            kickoff_away = recommended_away,
                            kickoff_frozen_at = NOW()
                        WHERE match_id = ?
                          AND kickoff_frozen_at IS NULL
                        """,
                matchId
        );
        return updated > 0;
    }

    public java.util.Set<Integer> findFrozenMatchPublicIds(int weekId) {
        List<Integer> ids = jdbcTemplate.queryForList(
                """
                        SELECT match_id FROM match_recommendation
                        WHERE week_id = ? AND kickoff_frozen_at IS NOT NULL
                        """,
                Integer.class,
                weekId
        );
        return new java.util.HashSet<>(ids);
    }

    public java.util.Map<Integer, int[]> findKickoffScoresByMatchIds(java.util.Collection<Integer> matchIds) {
        java.util.Map<Integer, int[]> result = new java.util.HashMap<>();
        if (matchIds == null || matchIds.isEmpty()) {
            return result;
        }
        List<Integer> ids = matchIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.query(
                """
                        SELECT match_id, kickoff_home, kickoff_away
                        FROM match_recommendation
                        WHERE kickoff_frozen_at IS NOT NULL
                          AND kickoff_home IS NOT NULL
                          AND kickoff_away IS NOT NULL
                          AND match_id IN (%s)
                        """.formatted(placeholders),
                rs -> {
                    result.put(
                            rs.getInt("match_id"),
                            new int[]{rs.getInt("kickoff_home"), rs.getInt("kickoff_away")}
                    );
                },
                ids.toArray()
        );
        return result;
    }

    public List<int[]> findFrozenTipResultsForCurrentSeason() {
        return jdbcTemplate.query(
                """
                        SELECT mr.kickoff_home, mr.kickoff_away, m.home_score, m.away_score
                        FROM match_recommendation mr
                        JOIN match m ON m.id = mr.match_id
                        JOIN weeks w ON w.id = mr.week_id
                        WHERE mr.kickoff_frozen_at IS NOT NULL
                          AND mr.kickoff_home IS NOT NULL
                          AND mr.kickoff_away IS NOT NULL
                          AND m.status = 'ft'
                          AND m.home_score IS NOT NULL
                          AND m.away_score IS NOT NULL
                          AND w.season_id = (SELECT season_id FROM weeks WHERE is_current LIMIT 1)
                        """,
                (rs, rowNum) -> new int[]{
                        rs.getInt("kickoff_home"), rs.getInt("kickoff_away"),
                        rs.getInt("home_score"), rs.getInt("away_score")
                }
        );
    }

    public void deleteUnfrozenRecommendationsForWeek(int weekId) {
        jdbcTemplate.update(
                "DELETE FROM match_recommendation WHERE week_id = ? AND kickoff_frozen_at IS NULL",
                weekId
        );
    }

    /** @deprecated use {@link #deleteUnfrozenRecommendationsForWeek(int)} */
    public void deleteRecommendationsForWeek(int weekId) {
        deleteUnfrozenRecommendationsForWeek(weekId);
    }

    public boolean hasRecommendationsForWeek(int weekId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_recommendation WHERE week_id = ?",
                Integer.class,
                weekId
        );
        return count != null && count > 0;
    }

    private RowMapper<TeamStatsSnapshot> teamMapper() {
        return (rs, rowNum) -> new TeamStatsSnapshot(
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

    private RowMapper<LeagueAveragesSnapshot> leagueMapper() {
        return (rs, rowNum) -> new LeagueAveragesSnapshot(
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
            ExplanationBreakdown explanation = parseExplanation(rs.getString("explanation_json"));
            Timestamp frozenAt = rs.getTimestamp("kickoff_frozen_at");
            Integer kickoffHome = getNullableInt(rs, "kickoff_home");
            Integer kickoffAway = getNullableInt(rs, "kickoff_away");
            return new MatchRecommendationSnapshot(
                    rs.getInt("match_id"),
                    rs.getInt("week_id"),
                    rs.getInt("recommended_home"),
                    rs.getInt("recommended_away"),
                    rs.getDouble("expected_home"),
                    rs.getDouble("expected_away"),
                    rs.getDouble("score_probability"),
                    explanation,
                    buildSummary(rs.getInt("recommended_home"), rs.getInt("recommended_away")),
                    rs.getTimestamp("computed_at").toInstant(),
                    kickoffHome,
                    kickoffAway,
                    frozenAt != null ? frozenAt.toInstant() : null,
                    getNullableInt(rs, "shadow_home"),
                    getNullableInt(rs, "shadow_away")
            );
        };
    }

    private ExplanationBreakdown parseExplanation(String json) {
        if (json == null || json.isBlank()) {
            return ExplanationBreakdown.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<String> notes = objectMapper.convertValue(node, new TypeReference<>() {
                });
                return new ExplanationBreakdown(List.of(), notes != null ? notes : List.of());
            }
            return objectMapper.treeToValue(node, ExplanationBreakdown.class);
        } catch (Exception e) {
            return ExplanationBreakdown.empty();
        }
    }

    private static String buildSummary(int home, int away) {
        return home + ":" + away;
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableDouble(java.sql.PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }
}
