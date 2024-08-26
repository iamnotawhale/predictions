package zhigalin.predictions.repository.football;

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
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class TeamDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public TeamDao(DataSource dataSource, PanicSender panicSender) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public void save(Team team) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO teams (public_id, code, logo, name)
                    VALUES (:public_id, :code, :logo, :name)
                    ON CONFLICT DO NOTHING
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("public_id", team.getPublicId());
            params.addValue("code", team.getCode());
            params.addValue("logo", team.getLogo());
            params.addValue("name", team.getName());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            panicSender.sendPanic("Error saving team: " + team.getCode(), e);
            serverLogger.error(e.getMessage());
        }
    }

    public Team findByName(String name) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM teams WHERE name = :name
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("name", name);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new TeamMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding team: " + name, e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public Team findByCode(String code) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM teams WHERE code = :code
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("code", code);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new TeamMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding team: " + code, e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public Team findByPublicId(int publicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM teams WHERE public_id = :publicId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("publicId", publicId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new TeamMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding team: " + publicId, e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Team> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM teams
                    """;
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, new TeamMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error while finding teams", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    private static final class TeamMapper implements RowMapper<Team> {

        @Override
        public Team mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Team.builder()
                    .publicId(rs.getInt("public_id"))
                    .name(rs.getString("name"))
                    .code(rs.getString("code"))
                    .logo(rs.getString("logo"))
                    .build();
        }
    }
}
