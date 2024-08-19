package zhigalin.predictions.repository.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.util.DaoUtil;

@Slf4j
@Repository
public class MatchDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MatchDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(Match match) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO match (public_id, week_id, home_team_id, away_team_id, home_team_score, away_team_score, status, result, local_date_time)
                    VALUES (:publicId, :weekId, :homeId, :awayId, :homeScore, :awayScore, :status, :result, :date)
                    ON CONFLICT ON CONSTRAINT unique_match DO NOTHING
                    """;

            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("publicId", match.getPublicId());
            parameters.addValue("weekId", match.getWeekId());
            parameters.addValue("homeId", match.getHomeTeamId());
            parameters.addValue("awayId", match.getAwayTeamId());
            parameters.addValue("homeScore", match.getHomeTeamScore());
            parameters.addValue("awayScore", match.getAwayTeamScore());
            parameters.addValue("status", match.getStatus());
            parameters.addValue("result", match.getResult());
            parameters.addValue("date", match.getLocalDateTime());
            namedParameterJdbcTemplate.update(sql, parameters);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public void updateMatch(Match match) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE match
                    SET home_team_score = :homeScore, away_team_score = :awayScore, result = :result, status = :status
                    WHERE public_id = :publicId
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeScore", match.getHomeTeamScore());
            parameters.addValue("awayScore", match.getAwayTeamScore());
            parameters.addValue("result", match.getResult());
            parameters.addValue("status", match.getStatus());
            parameters.addValue("publicId", match.getPublicId());
            namedParameterJdbcTemplate.update(sql, parameters);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public Match findByPublicId(int publicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM match WHERE public_id = :publicId
                    ORDER BY local_date_time DESC
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("publicId", publicId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllTodayMatches() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE CAST(local_date_time AS DATE) = CURRENT_DATE
                    ORDER BY local_date_time
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllMatchesInTheNextMinutes(int minutes) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE local_date_time BETWEEN :from AND :now
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            LocalDateTime now = LocalDateTime.now();
            parameters.addValue("from", now);
            parameters.addValue("to", now.plusMinutes(minutes));
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllByWeekIdOrderByLocalDateTime(int weekId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE week_id = :weekId
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("weekId", weekId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Match findMatchByTeamsPublicId(int homePublicId, int awayPublicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE home_team_id = :homePublicId AND away_team_id = :awayPublicId
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homePublicId", homePublicId);
            parameters.addValue("awayPublicId", awayPublicId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllByTeamPublicId(int teamPublicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE home_team_id = :teamPublicId OR away_team_id = :teamPublicId
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("teamPublicId", teamPublicId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllBetweenToDates(LocalDateTime from, LocalDateTime to) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE local_date_time > :from AND local_date_time < :to
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("from", from);
            parameters.addValue("to", to);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public Match findOnlineMatchByTeamId(int teamId) {
        LocalDateTime now = LocalDateTime.now();
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE local_date_time BETWEEN :from AND :to
                    AND (home_team_id = :teamId OR away_team_id = :teamId)
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("teamId", teamId);
            parameters.addValue("from", now.minusMinutes(140));
            parameters.addValue("to", now.plusMinutes(20));
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Integer> findOnlineTeamIds() {
        LocalDateTime now = LocalDateTime.now();
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT DISTINCT team_id
                    FROM (
                             SELECT home_team_id AS team_id
                             FROM match
                             WHERE local_date_time BETWEEN :from AND :to
                             UNION ALL
                             SELECT away_team_id AS team_id
                             FROM match
                             WHERE local_date_time BETWEEN :from AND :to
                         ) AS team_ids
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("from", now.minusHours(2));
            parameters.addValue("to", now);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForList(sql, parameters, Integer.class));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllByStatus(String status) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match
                    WHERE status = :status
                    ORDER BY local_date_time
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("status", status);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Match> findAllByCurrentWeek() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM match JOIN weeks w ON week_id = w.id
                    WHERE w.is_current is TRUE
                    ORDER BY local_date_time
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new MatchMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<Standing> getStandings() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    WITH MatchResults AS (
                        SELECT
                            m.home_team_id AS team_id,
                            m.away_team_id AS opponent_team_id,
                            m.home_team_score AS team_score,
                            m.away_team_score AS opponent_score
                        FROM match m
                        WHERE m.status NOT IN ('ns', 'pst')
                        UNION ALL
                        SELECT
                            m.away_team_id AS team_id,
                            m.home_team_id AS opponent_team_id,
                            m.away_team_score AS team_score,
                            m.home_team_score AS opponent_score
                        FROM match m
                        WHERE m.status NOT IN ('ns', 'pst')
                    )
                    SELECT
                        team_id,
                        COUNT(*) AS games,
                        SUM(CASE WHEN team_score > opponent_score THEN 1 ELSE 0 END) AS won,
                        SUM(CASE WHEN team_score = opponent_score THEN 1 ELSE 0 END) AS drawn,
                        SUM(CASE WHEN team_score < opponent_score THEN 1 ELSE 0 END) AS lost,
                        SUM(team_score) AS gf,
                        SUM(opponent_score) AS ga,
                        SUM(team_score) - SUM(opponent_score) AS gd,
                        SUM(CASE WHEN team_score > opponent_score THEN 3 ELSE 0 END) +
                        SUM(CASE WHEN team_score = opponent_score THEN 1 ELSE 0 END) AS points
                    FROM MatchResults
                    GROUP BY team_id
                    UNION ALL
                    SELECT
                        team_id, 0, 0, 0, 0, 0, 0, 0, 0
                    FROM (SELECT DISTINCT home_team_id AS team_id FROM match UNION SELECT DISTINCT away_team_id AS team_id FROM match) AS all_teams
                    WHERE team_id NOT IN (SELECT DISTINCT team_id FROM MatchResults)
                    ORDER BY points DESC, gd DESC, gf DESC, team_id
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new StandingMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static final class MatchMapper implements RowMapper<Match> {
        @Override
        public Match mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Match.builder()
                    .publicId(rs.getInt("public_id"))
                    .weekId(rs.getInt("week_id"))
                    .homeTeamId(rs.getInt("home_team_id"))
                    .awayTeamId(rs.getInt("away_team_id"))
                    .homeTeamScore(rs.getInt("home_team_score"))
                    .awayTeamScore(rs.getInt("away_team_score"))
                    .status(rs.getString("status"))
                    .result(rs.getString("result"))
                    .localDateTime(rs.getTimestamp("local_date_time").toLocalDateTime())
                    .build();
        }
    }

    private static final class StandingMapper implements RowMapper<Standing> {
        @Override
        public Standing mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Standing.builder()
                    .teamId(rs.getInt("team_id"))
                    .games(rs.getInt("games"))
                    .won(rs.getInt("won"))
                    .drawn(rs.getInt("drawn"))
                    .lost(rs.getInt("lost"))
                    .goalsFor(rs.getInt("gf"))
                    .goalsAgainst(rs.getInt("ga"))
                    .points(rs.getInt("points"))
                    .build();
        }
    }

}
