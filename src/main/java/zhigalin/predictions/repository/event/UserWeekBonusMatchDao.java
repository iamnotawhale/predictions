package zhigalin.predictions.repository.event;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.util.DaoUtil;

@Repository
@DependsOn("bonusMatchesSchemaMigration")
public class UserWeekBonusMatchDao {

    private final NamedParameterJdbcTemplate jdbc;

    public UserWeekBonusMatchDao(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public Optional<Integer> findMatchPublicId(int userId, int weekId) {
        String sql = """
                SELECT match_public_id FROM user_week_bonus_match
                WHERE user_id = :userId AND week_id = :weekId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("weekId", weekId);
        try {
            List<Integer> rows = jdbc.query(sql, params, (rs, i) -> rs.getInt("match_public_id"));
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void insert(int userId, int weekId, int matchPublicId) {
        String sql = """
                INSERT INTO user_week_bonus_match (user_id, week_id, match_public_id)
                VALUES (:userId, :weekId, :matchPublicId)
                ON CONFLICT (user_id, week_id) DO NOTHING
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("weekId", weekId)
                .addValue("matchPublicId", matchPublicId));
    }

    public List<Integer> findAllMatchIdsForWeek(int weekId) {
        String sql = """
                SELECT DISTINCT match_public_id FROM user_week_bonus_match WHERE week_id = :weekId
                """;
        try {
            return DaoUtil.getNullableResult(() -> jdbc.query(
                    sql, new MapSqlParameterSource("weekId", weekId), (rs, i) -> rs.getInt(1)));
        } catch (Exception e) {
            return List.of();
        }
    }

    public void deleteBeforeWeek(int minWeekId) {
        String sql = "DELETE FROM user_week_bonus_match WHERE week_id < :minWeekId";
        try {
            jdbc.update(sql, new MapSqlParameterSource("minWeekId", minWeekId));
        } catch (Exception ignored) {
            // schema may be mid-migration
        }
    }

    public void deleteAfterWeek(int maxWeekIdInclusive) {
        String sql = "DELETE FROM user_week_bonus_match WHERE week_id > :maxWeekId";
        try {
            jdbc.update(sql, new MapSqlParameterSource("maxWeekId", maxWeekIdInclusive));
        } catch (Exception ignored) {
            // schema may be mid-migration
        }
    }
}
