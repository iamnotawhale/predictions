package zhigalin.predictions.repository.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.HeadToHead;

@Slf4j
@Repository
public class HeadToHeadDao {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public HeadToHeadDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public void save(HeadToHead headToHead) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                    INSERT INTO h2h (home_team_id, away_team_id, home_team_score, away_team_score, league_name, local_date_time)
                    VALUES (:homeTeamId, :awayTeamId, :homeTeamScore, :awayTeamScore, :leagueName, :local_date_time)
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeTeamId", headToHead.getHomeTeamId());
            parameters.addValue("awayTeamId", headToHead.getAwayTeamId());
            parameters.addValue("homeTeamScore", headToHead.getHomeTeamScore());
            parameters.addValue("awayTeamScore", headToHead.getAwayTeamScore());
            parameters.addValue("leagueName", headToHead.getLeagueName());
            parameters.addValue("localDateTime", headToHead.getLocalDateTime());
            namedParameterJdbcTemplate.update(sql, parameters);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
    }


    public List<HeadToHead> getH2hByTeamsCode(String homeTeamCode, String awayTeamCode) {
        try (Connection ignored = dataSource.getConnection()) {
            String sql = """
                        SELECT home_team_id, away_team_id, home_team_score, away_team_score, league_name, local_date_time
                        FROM h2h
                                 JOIN teams ht on ht.id = home_team_id
                                 JOIN teams at on at.id = away_team_id
                        WHERE ht.code in (:homeTeamCode, :awayTeamCode)
                        AND at.code in (:homeTeamCode, :awayTeamCode);
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeTeamCode", homeTeamCode);
            parameters.addValue("awayTeamCode", awayTeamCode);
            return namedParameterJdbcTemplate.query(sql, parameters, new HeadToHeadMapper());
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static final class HeadToHeadMapper implements RowMapper<HeadToHead> {

        @Override
        public HeadToHead mapRow(ResultSet rs, int rowNum) throws SQLException {
            return HeadToHead.builder()
                    .homeTeamId(rs.getInt("home_team_id"))
                    .awayTeamId(rs.getInt("away_team_id"))
                    .homeTeamScore(rs.getInt("home_team_score"))
                    .awayTeamScore(rs.getInt("away_team_score"))
                    .leagueName(rs.getString("league_name"))
                    .localDateTime(rs.getTimestamp("local_date_time").toLocalDateTime())
                    .build();
        }
    }
}
