package zhigalin.predictions.repository.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    public UserDao(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<User> findAll() {
        try {
            String sql = """
                    SELECT * FROM users
                    """;
            return DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new UserMapper()));
        } catch (Exception e) {
            serverLogger.error(e.getMessage());
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
