package zhigalin.predictions.repository.predict;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
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
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class PredictionDao {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public PredictionDao(DataSource dataSource, PanicSender panicSender) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.panicSender = panicSender;
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
            panicSender.sendPanic("Error saving prediction", e);
            serverLogger.error(e.getMessage());
        }
    }

    public void save(String telegramId, String homeTeam, String awayTeam, int homeTeamScore, int awayTeamScore) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    WITH user_id AS (
                        SELECT id FROM users WHERE telegram_id = :telegramId
                    ), match_id AS (
                        SELECT public_id FROM match
                        WHERE home_team_id IN (SELECT public_id FROM teams WHERE code = :homeTeam)
                        AND away_team_id IN (SELECT public_id FROM teams WHERE code = :awayTeam)
                    )
                    INSERT INTO predict (user_id, match_id, home_team_score, away_team_score, points)
                    VALUES (
                        (SELECT id FROM user_id),
                        (SELECT public_id FROM match_id),
                        :homeTeamScore,
                        :awayTeamScore,
                        :points
                    )
                    ON CONFLICT ON CONSTRAINT unique_predict DO UPDATE SET
                    home_team_score = excluded.home_team_score,
                    away_team_score = excluded.away_team_score;
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("telegramId", telegramId);
            params.addValue("homeTeam", homeTeam);
            params.addValue("awayTeam", awayTeam);
            params.addValue("homeTeamScore", homeTeamScore);
            params.addValue("awayTeamScore", awayTeamScore);
            params.addValue("points", null);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            panicSender.sendPanic("Error saving prediction", e);
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
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            panicSender.sendPanic("Error deleting prediction", e);
            serverLogger.error(e.getMessage());
        }
    }

    public void deleteByUserTelegramIdAndTeams(String telegramId, String homeTeam, String awayTeam) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    DELETE FROM predict
                    WHERE user_id IN (
                        SELECT id
                        FROM users
                        WHERE telegram_id = :telegramId
                    )
                    AND match_id IN (
                        SELECT public_id
                        FROM match
                        WHERE home_team_id IN (
                            SELECT public_id
                            FROM teams
                            WHERE code = :homeTeam
                        )
                          AND away_team_id IN (
                            SELECT public_id
                            FROM teams
                            WHERE code = :awayTeam
                        )
                    )
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("telegramId", telegramId);
            params.addValue("homeTeam", homeTeam);
            params.addValue("awayTeam", awayTeam);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            panicSender.sendPanic("Error deleting prediction by user telegram id and teams", e);
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
            panicSender.sendPanic("Error finding prediction by match id and user id", e);
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
            panicSender.sendPanic("Error finding prediction by match ids", e);
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
            panicSender.sendPanic("Error finding prediction by user id", e);
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
            panicSender.sendPanic("Error updating prediction points", e);
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
            panicSender.sendPanic("Error finding prediction by matches", e);
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
            panicSender.sendPanic("Error finding all by week id", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<MatchPrediction> findAllByWeekIdAndUserTelegramId(int weekId, String telegramId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT m.home_team_id, m.away_team_id, m.home_team_score, m.away_team_score, m.public_id, m.result,
                       m.status, m.week_id, m.local_date_time,
                       p.user_id, p.home_team_score as predict_hts, p.away_team_score as predict_ats, p.points
                    FROM match m
                    JOIN predict p ON m.public_id = p.match_id
                    JOIN users u ON p.user_id = u.id
                    WHERE m.week_id = :weekId AND u.telegram_id = :telegramId
                    ORDER BY m.local_date_time DESC, p.user_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("weekId", weekId);
            params.addValue("telegramId", telegramId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, params, new MatchPredictionMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error finding all by week id", e);
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
            panicSender.sendPanic("Error finding get points by user id", e);
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
            panicSender.sendPanic("Error get all points by users", e);
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
            panicSender.sendPanic("Error get all points by week id", e);
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
            panicSender.sendPanic("Error get predictions by user id and week id", e);
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
            panicSender.sendPanic("Error prediction is exist", e);
            serverLogger.error(e.getMessage());
            return false;
        }
    }

    public boolean isExist(String userTelegramId, int matchId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT EXISTS(
                        SELECT 1 FROM predict
                        JOIN users u ON user_id = u.id
                        WHERE match_id = :matchId AND u.telegram_id = :userTelegramId
                    )
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("matchId", matchId);
            params.addValue("userTelegramId", userTelegramId);
            return Boolean.TRUE.equals(namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class));
        } catch (SQLException e) {
            panicSender.sendPanic("Error prediction is exist", e);
            serverLogger.error(e.getMessage());
            return false;
        }
    }

    public Prediction getByUserTelegramIdAndTeams(String telegramId, String homeTeam, String awayTeam) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT *
                    FROM predict
                    WHERE user_id IN (SELECT id
                                      FROM users
                                      WHERE telegram_id = :telegramId)
                      AND match_id IN (SELECT public_id
                                       FROM match
                                       WHERE home_team_id IN (SELECT public_id
                                                              FROM teams
                                                              WHERE code = :homeTeam)
                                         AND away_team_id IN (SELECT public_id
                                                              FROM teams
                                                              WHERE code = :awayTeam))
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("telegramId", telegramId);
            params.addValue("homeTeam", homeTeam);
            params.addValue("awayTeam", awayTeam);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new PredictionMapper()));
        } catch (SQLException e) {
            panicSender.sendPanic("Error prediction is exist", e);
            serverLogger.error(e.getMessage());
            return null;
        }
    }

    public List<Integer> findPredictableWeeksByUserTelegramId(String telegramId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT DISTINCT w.id
                    FROM weeks w
                    JOIN (
                        SELECT DISTINCT m.week_id
                        FROM predict p
                        JOIN match m ON p.match_id = m.public_id
                        WHERE p.user_id IN (SELECT id FROM users WHERE telegram_id = :telegramId)
                    ) AS subquery ON w.id = subquery.week_id;
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("telegramId", telegramId);
            return namedParameterJdbcTemplate.queryForList(sql, params, Integer.class);
        } catch (SQLException e) {
            panicSender.sendPanic("Error on find predictable weeks by user telegram id", e);
            serverLogger.error(e.getMessage());
            return Collections.emptyList();
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
                    .homeTeamScore(rs.getObject("predict_hts", Integer.class))
                    .awayTeamScore(rs.getObject("predict_ats", Integer.class))
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
                    .homeTeamScore(rs.getObject("home_team_score", Integer.class))
                    .awayTeamScore(rs.getObject("away_team_score", Integer.class))
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

