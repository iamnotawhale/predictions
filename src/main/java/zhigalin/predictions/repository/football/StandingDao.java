package zhigalin.predictions.repository.football;

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
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.util.DaoUtil;

@Slf4j
@Repository
public class StandingDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public StandingDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(Standing standing) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO standing (team_id, games, won, draw, lost, goals_scored, goals_against, points)
                    VALUES (:teamId, :games, :won, :draw, :lost, :goalsScored, :goalsAgainst, :points)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("teamId", standing.getTeamId());
            params.addValue("games", standing.getGames());
            params.addValue("won", standing.getWon());
            params.addValue("draw", standing.getDraw());
            params.addValue("lost", standing.getLost());
            params.addValue("goalsScored", standing.getGoalsScored());
            params.addValue("goalsAgainst", standing.getGoalsAgainst());
            params.addValue("points", standing.getPoints());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public void update(Standing standing) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE standing
                    SET games = :games, won = :won, draw = :draw, lost = :lost, goals_scored = :goalsScored, goals_against = :goalsAgainst, points = :points
                    WHERE team_id = :teamId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("teamId", standing.getTeamId());
            params.addValue("games", standing.getGames());
            params.addValue("won", standing.getWon());
            params.addValue("draw", standing.getDraw());
            params.addValue("lost", standing.getLost());
            params.addValue("goalsScored", standing.getGoalsScored());
            params.addValue("goalsAgainst", standing.getGoalsAgainst());
            params.addValue("points", standing.getPoints());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public Standing findByTeamPublicId(int publicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM standing WHERE team_id = :teamId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("teamId", publicId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new StandingMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Standing> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM standing
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new StandingMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }


    private static final class StandingMapper implements RowMapper<Standing> {
        @Override
        public Standing mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Standing.builder()
                    .games(rs.getInt("games"))
                    .points(rs.getInt("points"))
                    .won(rs.getInt("won"))
                    .draw(rs.getInt("draw"))
                    .lost(rs.getInt("lost"))
                    .goalsScored(rs.getInt("goals_scored"))
                    .goalsAgainst(rs.getInt("goals_against"))
                    .teamId(rs.getInt("team_id"))
                    .build();
        }
    }
}
