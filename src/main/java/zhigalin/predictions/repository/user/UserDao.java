package zhigalin.predictions.repository.user;

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
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.util.DaoUtil;

@Slf4j
@Repository
public class UserDao {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public User findByLogin(String login) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM users WHERE login = :login
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("login", login);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new UserMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public User findByTelegramId(String telegramId) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM users WHERE telegram_id = :telegramId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("telegramId", telegramId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new UserMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public void save(User user) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO users (login, password, role, telegram_id)
                    VALUES (:login, :password, :role, :telegramId)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("login", user.getLogin());
            params.addValue("password", user.getPassword());
            params.addValue("role", user.getRole());
            params.addValue("telegramId", user.getTelegramId());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    public List<User> findAll() {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM users
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new UserMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public User findById(int id) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    SELECT * FROM users WHERE id = :id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("id", id);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(sql, params, new UserMapper()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static final class UserMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return User.builder()
                    .id(rs.getInt("id"))
                    .login(rs.getString("login"))
                    .role(rs.getString("role"))
                    .password(rs.getString("password"))
                    .telegramId(rs.getString("telegram_id"))
                    .build();
        }
    }
}
