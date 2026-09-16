package zhigalin.predictions.repository.predict;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.service.predict.FlooredPointsService.OrderedPointsRow;

@Repository
@DependsOn("bonusMatchesSchemaMigration")
public class PointEventDao {

    private final NamedParameterJdbcTemplate jdbc;

    public PointEventDao(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public List<OrderedPointsRow> findSeasonFinishedEvents() {
        String sql = """
                SELECT u.login AS login,
                       u.id AS user_id,
                       COALESCE(m.finished_at, m.local_date_time) AS sort_time,
                       m.public_id AS sequence,
                       m.week_id AS week_id,
                       p.points AS points,
                       FALSE AS cup
                FROM predict p
                JOIN users u ON u.id = p.user_id
                JOIN match m ON m.public_id = p.match_id
                WHERE p.match_id IS NOT NULL
                  AND m.status = 'ft'
                UNION ALL
                SELECT u.login,
                       u.id,
                       COALESCE(b.finished_at, b.local_date_time),
                       b.public_id + 1000000000,
                       0,
                       p.points,
                       TRUE
                FROM predict p
                JOIN users u ON u.id = p.user_id
                JOIN bonus_match b ON b.public_id = p.bonus_match_id
                WHERE p.bonus_match_id IS NOT NULL
                  AND b.status = 'ft'
                ORDER BY login, sort_time NULLS LAST, sequence
                """;
        return query(sql, new MapSqlParameterSource());
    }

    public List<OrderedPointsRow> findWeekFinishedEvents(int weekId) {
        String sql = """
                SELECT u.login AS login,
                       u.id AS user_id,
                       COALESCE(m.finished_at, m.local_date_time) AS sort_time,
                       m.public_id AS sequence,
                       m.week_id AS week_id,
                       p.points AS points,
                       FALSE AS cup
                FROM predict p
                JOIN users u ON u.id = p.user_id
                JOIN match m ON m.public_id = p.match_id
                WHERE p.match_id IS NOT NULL
                  AND m.status = 'ft'
                  AND m.week_id = :weekId
                ORDER BY login, sort_time NULLS LAST, sequence
                """;
        return query(sql, new MapSqlParameterSource("weekId", weekId));
    }

    private List<OrderedPointsRow> query(String sql, MapSqlParameterSource params) {
        try {
            return jdbc.query(sql, params, (rs, i) -> {
                Timestamp ts = rs.getTimestamp("sort_time");
                return new OrderedPointsRow(
                        rs.getString("login"),
                        rs.getInt("user_id"),
                        ts != null ? ts.toLocalDateTime() : null,
                        rs.getLong("sequence"),
                        rs.getInt("week_id"),
                        (Integer) rs.getObject("points"),
                        rs.getBoolean("cup")
                );
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
