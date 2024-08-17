package zhigalin.predictions.repository.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;

@Slf4j
@Repository
public class WeekDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public WeekDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public Week save(Week week) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT into weeks (id, is_current, name, season_id)
                    VALUES (:id, :isCurrent, :name, :seasonId)
                    RETURNING *
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", week.getId());
            parameters.addValue("isCurrent", week.getIsCurrent());
            parameters.addValue("name", week.getName());
            parameters.addValue("seasonId", week.getSeasonId());
            return namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Week findByNameAndSeasonId(String name, int seasonId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    WHERE name = :name AND season_id = :seasonId;
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("name", name);
            parameters.addValue("seasonId", seasonId);
            return namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Week findByIsCurrentTrue() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    WHERE is_current = TRUE;
                    """;
            return jdbcTemplate.queryForObject(sql, new WeekMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public void updateCurrentWeek(int id, Boolean isCurrent) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE weeks
                    SET is_current = :isCurrent
                    WHERE id = :id;
                    """;
            jdbcTemplate.update(sql);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public Week findById(int id) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    WHERE id = :id;
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", id);
            return namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Week> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    """;
            return jdbcTemplate.query(sql, new WeekMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static final class WeekMapper implements RowMapper<Week> {
        @Override
        public Week mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Week.builder()
                    .id(rs.getInt("id"))
                    .name(rs.getString("name"))
                    .isCurrent(rs.getBoolean("is_current"))
                    .seasonId(rs.getInt("season_id"))
                    .build();
        }
    }
}
