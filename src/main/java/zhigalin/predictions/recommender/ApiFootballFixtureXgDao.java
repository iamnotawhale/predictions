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
public class ApiFootballFixtureXgDao {

    private final JdbcTemplate jdbcTemplate;

    public ApiFootballFixtureXgDao(JdbcTemplate jdbcTemplate) {
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
                "SELECT fixture_id FROM api_football_fixture_xg WHERE fixture_id IN (" + placeholders + ")",
                (rs, rowNum) -> rs.getInt(1),
                args
        );
        return new HashSet<>(rows);
    }

    public void upsert(int fixtureId, int homeTeamId, int awayTeamId, double homeXg, double awayXg, Instant fetchedAt) {
        write(fixtureId, homeTeamId, awayTeamId, homeXg, awayXg, fetchedAt);
    }

    public void upsertUnavailable(int fixtureId, int homeTeamId, int awayTeamId, Instant fetchedAt) {
        write(fixtureId, homeTeamId, awayTeamId, null, null, fetchedAt);
    }

    private void write(
            int fixtureId,
            int homeTeamId,
            int awayTeamId,
            Double homeXg,
            Double awayXg,
            Instant fetchedAt
    ) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO api_football_fixture_xg (
                        fixture_id, home_team_id, away_team_id, home_xg, away_xg, fetched_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (fixture_id) DO UPDATE SET
                        home_team_id = EXCLUDED.home_team_id,
                        away_team_id = EXCLUDED.away_team_id,
                        home_xg = EXCLUDED.home_xg,
                        away_xg = EXCLUDED.away_xg,
                        fetched_at = EXCLUDED.fetched_at
                    """);
            ps.setInt(1, fixtureId);
            ps.setInt(2, homeTeamId);
            ps.setInt(3, awayTeamId);
            setNullableDouble(ps, 4, homeXg);
            setNullableDouble(ps, 5, awayXg);
            ps.setTimestamp(6, Timestamp.from(fetchedAt));
            return ps;
        });
    }

    public List<XgRow> findAll() {
        return jdbcTemplate.query(
                "SELECT fixture_id, home_team_id, away_team_id, home_xg, away_xg FROM api_football_fixture_xg",
                (rs, rowNum) -> new XgRow(
                        rs.getInt("fixture_id"),
                        rs.getInt("home_team_id"),
                        rs.getInt("away_team_id"),
                        getNullableDouble(rs, "home_xg"),
                        getNullableDouble(rs, "away_xg")
                )
        );
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setDouble(index, value);
        }
    }

    public record XgRow(int fixtureId, int homeTeamId, int awayTeamId, Double homeXg, Double awayXg) {
        boolean hasXg() {
            return homeXg != null && awayXg != null;
        }
    }
}
