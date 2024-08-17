package zhigalin.predictions.repository.user;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PointsDao;

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
                    
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource();
            namedParameterJdbcTemplate.query(sql, params, new PointsDao.PointsMapper());
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }

    User findByTelegramId(String telegramId);

    Optional<User> findById(int id);
}
