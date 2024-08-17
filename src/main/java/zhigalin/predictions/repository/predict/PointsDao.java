package zhigalin.predictions.repository.predict;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Points;

@Slf4j
@Repository
public class PointsDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PointsDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(Points points) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO points (user_id, value)
                    VALUES (:user_id, :value)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", points.getUserId());
            params.addValue("value", points.getValue());
            namedParameterJdbcTemplate.query(sql, params, new PointsMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public void update(int userId, int value) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE points
                    SET value = :value
                    WHERE user_id = :userId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            params.addValue("value", value);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public Points getPointsByUserId(int userId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM points WHERE user_id = :userId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            return namedParameterJdbcTemplate.queryForObject(sql, params, new PointsMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Integer getPointsByUserIdAndMatchWeekId(int userId, int weekId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT sum(p.points) FROM predict p left join match m on p.match_id = m.public_id
                    WHERE m.week_id = :weekId and p.user_id = :userId
                    GROUP BY p.user_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            params.addValue("weekId", weekId);
            return namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Integer getAllPointsByUserId(int userId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT sum(points) FROM predict
                    WHERE user_id = :userId
                    GROUP BY user_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            return namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Points> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM points
                    """;
            return jdbcTemplate.query(sql, new PointsMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static final class PointsMapper implements RowMapper<Points> {
        @Override
        public Points mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Points.builder()
                    .userId(rs.getInt("user_id"))
                    .value(rs.getInt("value"))
                    .build();
        }
    }
}
