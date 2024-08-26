package zhigalin.predictions.repository.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class WeekDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public WeekDao(DataSource dataSource, PanicSender panicSender) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public Week save(Week week) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT into weeks (is_current, name, season_id)
                    VALUES (:isCurrent, :name, :seasonId)
                    RETURNING *
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", week.getId());
            parameters.addValue("isCurrent", week.getIsCurrent());
            parameters.addValue("name", week.getName());
            parameters.addValue("seasonId", week.getSeasonId());
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while saving week", e);
            serverLogger.error(e.getMessage());
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
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding week by name and season id", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public Week findByIsCurrentTrue() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    WHERE is_current = TRUE;
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.queryForObject(sql, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding current week", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public void updateCurrentWeek() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE weeks
                    SET is_current = false
                    WHERE id = (
                        SELECT id
                        FROM weeks
                        WHERE is_current = true
                    )
                    RETURNING id
                    """;
            Integer id = jdbcTemplate.queryForObject(sql, Integer.class);
            sql = """
                    UPDATE weeks
                    SET is_current = true
                    WHERE id = :id
                    """;
            namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource().addValue("id", id + 1));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while updating current week", e);
            serverLogger.error(e.getMessage());
        }
    }

    public Week findById(int id) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    WHERE id = :id
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", id);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding week by id", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Week> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM weeks
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding all weeks", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public Week findByMatchId(int matchId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT weeks.* FROM weeks
                    JOIN match m ON weeks.id = m.week_id
                    WHERE m.public_id = :matchId
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("matchId", matchId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new WeekMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding week by match id", e);
            serverLogger.error(e.getMessage());
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
