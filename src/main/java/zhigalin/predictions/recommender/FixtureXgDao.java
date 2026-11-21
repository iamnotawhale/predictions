package zhigalin.predictions.recommender;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FixtureXgDao {

    private final JdbcTemplate jdbcTemplate;

    public FixtureXgDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<Integer> findCachedFixtureIds(Collection<Integer> fixtureIds) {
        if (fixtureIds == null || fixtureIds.isEmpty()) {
            return Set.of();
        }
        StringBuilder placeholders = new StringBuilder();
        Object[] args = new Object[fixtureIds.size()];
        int i = 0;
        for (Integer id : fixtureIds) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
            args[i++] = id;
        }
        List<Integer> rows = jdbcTemplate.query(
                "SELECT match_id FROM fixture_xg WHERE match_id IN (" + placeholders + ")"
                        + " AND home_xg IS NOT NULL AND away_xg IS NOT NULL",
                (rs, rowNum) -> rs.getInt(1),
                args
        );
        return new HashSet<>(rows);
    }

    public void upsert(int matchId, int homeTeamId, int awayTeamId, double homeXg, double awayXg, Instant fetchedAt) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO fixture_xg (
                        match_id, home_team_id, away_team_id, home_xg, away_xg, fetched_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (match_id) DO UPDATE SET
                        home_team_id = EXCLUDED.home_team_id,
                        away_team_id = EXCLUDED.away_team_id,
                        home_xg = EXCLUDED.home_xg,
                        away_xg = EXCLUDED.away_xg,
                        fetched_at = EXCLUDED.fetched_at
                    """);
            ps.setInt(1, matchId);
            ps.setInt(2, homeTeamId);
            ps.setInt(3, awayTeamId);
            ps.setDouble(4, homeXg);
            ps.setDouble(5, awayXg);
            ps.setTimestamp(6, Timestamp.from(fetchedAt));
            return ps;
        });
    }

    public List<XgRow> findAllWithXg() {
        return jdbcTemplate.query(
                """
                        SELECT match_id, home_team_id, away_team_id, home_xg, away_xg
                        FROM fixture_xg
                        WHERE home_xg IS NOT NULL AND away_xg IS NOT NULL
                        """,
                (rs, rowNum) -> new XgRow(
                        rs.getInt("match_id"),
                        rs.getInt("home_team_id"),
                        rs.getInt("away_team_id"),
                        requireDouble(rs, "home_xg"),
                        requireDouble(rs, "away_xg")
                )
        );
    }

    private static double requireDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        if (rs.wasNull()) {
            throw new SQLException("Unexpected null in " + column);
        }
        return value;
    }

    public record XgRow(int matchId, int homeTeamId, int awayTeamId, double homeXg, double awayXg) {
    }
}
