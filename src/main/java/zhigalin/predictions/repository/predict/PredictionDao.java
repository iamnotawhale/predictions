package zhigalin.predictions.repository.predict;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Points;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class PredictionDao {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    public PredictionDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(Prediction prediction) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO predict (user_id, match_id, home_team_score, away_team_score, points)
                    VALUES (:userId, :matchId, :homeTeamScore, :awayTeamScore, :points)
                    ON CONFLICT ON CONSTRAINT unique_predict DO UPDATE SET
                    home_team_score = excluded.home_team_score,
                    away_team_score = excluded.away_team_score
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", prediction.getUserId());
            params.addValue("matchId", prediction.getMatchPublicId());
            params.addValue("homeTeamScore", prediction.getHomeTeamScore());
            params.addValue("awayTeamScore", prediction.getAwayTeamScore());
            params.addValue("points", prediction.getPoints());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
        }
    }

    public void delete(int userId, int matchPublicId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    DELETE FROM predict WHERE user_id = :userId AND match_id = :matchPublicId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            params.addValue("matchPublicId", matchPublicId);
            namedParameterJdbcTemplate.query(sql, params, new PredictionMapper());
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
        }
    }

    public Prediction findByMatchIdAndUserId(int matchId, int userId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM predict WHERE user_id = :userId AND match_id = :matchId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            params.addValue("matchId", matchId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new PredictionMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Prediction> findAllByMatchIds(List<Integer> matchIds) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM predict
                    JOIN match m ON match_id = m.public_id
                    WHERE match_id IN (:matchIds)
                    ORDER BY m.local_date_time DESC, m.home_team_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("matchIds", matchIds);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new PredictionMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<MatchPrediction> findAllByUserId(int userId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT m.home_team_id, m.away_team_id, m.home_team_score, m.away_team_score, m.public_id, m.result,
                       m.status, m.week_id, m.local_date_time,
                       p.user_id, p.home_team_score as predict_hts, p.away_team_score as predict_ats, p.points
                    FROM match m
                    JOIN predict p ON m.public_id = p.match_id
                    WHERE user_id = :userId
                    ORDER BY m.local_date_time DESC
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new MatchPredictionMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public void updatePoints(int matchId, int userId, int points) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    UPDATE predict SET
                    points = :points WHERE match_id = :matchId AND user_id = :userId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("points", points);
            params.addValue("matchId", matchId);
            params.addValue("userId", userId);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
        }
    }

    public List<Prediction> getAllByMatches(List<Match> matches) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM predict
                    WHERE match_id IN (:matchIds)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("matchIds", matches.stream().map(Match::getPublicId).toList());
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new PredictionMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<MatchPrediction> findAllByWeekId(int weekId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT m.home_team_id, m.away_team_id, m.home_team_score, m.away_team_score, m.public_id, m.result,
                       m.status, m.week_id, m.local_date_time,
                       p.user_id, p.home_team_score as predict_hts, p.away_team_score as predict_ats, p.points
                    FROM match m
                    JOIN predict p ON m.public_id = p.match_id
                    WHERE m.week_id = :weekId
                    ORDER BY m.local_date_time DESC, p.user_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("weekId", weekId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new MatchPredictionMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public Points getPointsByUserId(int userId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT u.login as login, sum(points) as value
                    FROM predict
                    JOIN users u ON user_id = u.id
                    WHERE u.id = :userId
                    GROUP BY login
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("userId", userId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new PointsMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Points> getAllPointsByUsers() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT u.login as login, sum(points) as value
                    FROM predict
                    JOIN users u ON user_id = u.id
                    GROUP BY login
                    ORDER BY value;
                    """;

            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new PointsMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Points> getAllPointsByWeekId(int weekId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT u.login as login, sum(points) as value
                    FROM predict
                    JOIN users u ON user_id = u.id
                    JOIN match m ON match_id = m.public_id
                    JOIN weeks w ON m.week_id = w.id
                    WHERE w.id = :weekId
                    GROUP BY login
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("weekId", weekId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new PointsMapper()));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<MatchPrediction> getPredictionsByUserAndWeek(int userId, int weekId) {
        Map<Match, Prediction> result = new HashMap<>();
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT m.home_team_id, m.away_team_id, m.home_team_score, m.away_team_score, m.public_id, m.result,
                           m.status, m.week_id, m.local_date_time,
                           p.user_id, p.home_team_score as predict_hts, p.away_team_score as predict_ats, p.points
                    FROM match m
                    JOIN predict p ON m.public_id = p.match_id
                    WHERE m.week_id = :weekId AND user_id = :userId
                    """;

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("weekId", weekId);
            params.addValue("userId", userId);

            return namedParameterJdbcTemplate.query(sql, params, new MatchPredictionMapper());
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public boolean isExist(int userId, int matchId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT EXISTS(SELECT 1 FROM predict WHERE match_id = :matchId AND user_id = :userId)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("matchId", matchId);
            params.addValue("userId", userId);
            return Boolean.TRUE.equals(namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class));
        } catch (SQLException e) {
            serverLogger.error(e.getMessage());
            return false;
        }
    }

    private static class MatchPredictionMapper implements RowMapper<MatchPrediction> {
        @Override
        public MatchPrediction mapRow(ResultSet rs, int rowNum) throws SQLException {

            Match match = Match.builder()
                    .homeTeamId(rs.getInt("home_team_id"))
                    .awayTeamId(rs.getInt("away_team_id"))
                    .homeTeamScore(rs.getInt("home_team_score"))
                    .awayTeamScore(rs.getInt("away_team_score"))
                    .publicId(rs.getInt("public_id"))
                    .result(rs.getString("result"))
                    .status(rs.getString("status"))
                    .weekId(rs.getInt("week_id"))
                    .localDateTime(rs.getTimestamp("local_date_time").toLocalDateTime())
                    .build();

            Prediction prediction = Prediction.builder()
                    .userId(rs.getInt("user_id"))
                    .homeTeamScore(rs.getInt("predict_hts"))
                    .awayTeamScore(rs.getInt("predict_ats"))
                    .points(rs.getInt("points"))
                    .build();
            return new MatchPrediction(match, prediction);
        }
    }

    private static final class PredictionMapper implements RowMapper<Prediction> {
        @Override
        public Prediction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Prediction.builder()
                    .userId(rs.getInt("user_id"))
                    .matchPublicId(rs.getInt("match_id"))
                    .homeTeamScore(rs.getInt("home_team_score"))
                    .awayTeamScore(rs.getInt("away_team_score"))
                    .points(rs.getInt("points"))
                    .build();
        }
    }

    private static final class PointsMapper implements RowMapper<Points> {
        @Override
        public Points mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Points.builder()
                    .login(rs.getString("login"))
                    .value(rs.getInt("value"))
                    .build();
        }
    }

    public record MatchPrediction(Match match, Prediction prediction) {
    }
}

