package zhigalin.predictions.repository.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

@Repository
public class HeadToHeadDao {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public HeadToHeadDao(DataSource dataSource, PanicSender panicSender) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public void save(HeadToHead headToHead) {
        try {
            String sql = """
                    INSERT INTO h2h (home_team_id, away_team_id, home_score, away_score, league_name, starts_at)
                    VALUES (:homeTeamId, :awayTeamId, :homeTeamScore, :awayTeamScore, :leagueName, :localDateTime)
                    ON CONFLICT ON CONSTRAINT unique_h2h DO NOTHING
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeTeamId", headToHead.getHomeTeamId());
            parameters.addValue("awayTeamId", headToHead.getAwayTeamId());
            parameters.addValue("homeTeamScore", headToHead.getHomeScore());
            parameters.addValue("awayTeamScore", headToHead.getAwayScore());
            parameters.addValue("leagueName", headToHead.getLeagueName());
            parameters.addValue("localDateTime", headToHead.getStartsAt());
            int update = namedParameterJdbcTemplate.update(sql, parameters);
            if (update == 1) {
                serverLogger.info("Saved head to head: {}", headToHead);
            }
        } catch (Exception e) {
            panicSender.sendPanic("Saving h2h DB error", e);
            serverLogger.error(e.getMessage());
        }
    }


    public List<HeadToHead> getH2hByTeamsCode(String homeTeamCode, String awayTeamCode) {
        try {
            String sql = """
                        SELECT home_team_id, away_team_id, home_score, away_score, league_name, starts_at
                        FROM h2h
                                 JOIN teams ht on ht.id = home_team_id
                                 JOIN teams at on at.id = away_team_id
                        WHERE ht.code in (:homeTeamCode, :awayTeamCode)
                        AND at.code in (:homeTeamCode, :awayTeamCode);
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeTeamCode", homeTeamCode);
            parameters.addValue("awayTeamCode", awayTeamCode);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new HeadToHeadMapper()));
        } catch (Exception e) {
            panicSender.sendPanic("Getting h2h by team codes error", e);
            serverLogger.error(e.getMessage());
            return List.of();
        }
    }

    public List<HeadToHead> getAllByTeamsIds(int homeTeamId, int awayTeamId) {
        try {
            String sql = """
                        SELECT home_team_id, away_team_id, home_score, away_score, league_name, starts_at
                        FROM h2h
                                 JOIN teams ht on ht.id = home_team_id
                                 JOIN teams at on at.id = away_team_id
                        WHERE ht.id in (:homeTeamId, :awayTeamId)
                        AND at.id in (:homeTeamId, :awayTeamId);
                    """;
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("homeTeamId", homeTeamId);
            parameters.addValue("awayTeamId", awayTeamId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(sql, parameters, new HeadToHeadMapper()));
        } catch (Exception e) {
            panicSender.sendPanic("Getting all h2h by teams ids error", e);
            serverLogger.error(e.getMessage());
            return List.of();
        }
    }

    public Map<Integer, List<HeadToHead>> findAllByCurrentWeek() {
        try {
            String sql = """
                        SELECT
                            m.id,
                            h2h.home_team_id,
                            h2h.away_team_id,
                            h2h.home_score,
                            h2h.away_score,
                            h2h.league_name,
                            h2h.starts_at
                        FROM match m
                        JOIN weeks w ON m.week_id = w.id
                        JOIN h2h ON
                            (m.home_team_id = h2h.home_team_id AND m.away_team_id = h2h.away_team_id)
                                OR
                            (m.home_team_id = h2h.away_team_id AND m.away_team_id = h2h.home_team_id)
                        WHERE w.is_current = TRUE
                        ORDER BY m.starts_at, h2h.starts_at DESC
                    """;
            List<MatchHeadToHead> allMatchHeadToHeads = DaoUtil.getNullableResult(() -> jdbcTemplate.query(sql, new MatchHeadToHeadMapper()));

            if (allMatchHeadToHeads != null && !allMatchHeadToHeads.isEmpty()) {
                Map<Integer, List<HeadToHead>> headToHeadsByMatch = new HashMap<>();
                for (MatchHeadToHead matchHeadToHead : allMatchHeadToHeads) {
                    headToHeadsByMatch.computeIfAbsent(matchHeadToHead.matchId, k -> new ArrayList<>()).add(matchHeadToHead.headToHead);
                }


                Map<Integer, List<HeadToHead>> result = new HashMap<>();
                headToHeadsByMatch.forEach((matchId, headToHeads) -> {
                    result.put(
                            matchId,
                            headToHeads.stream()
                                    .sorted(Comparator.comparing(HeadToHead::getStartsAt).reversed())
                                    .limit(7)
                                    .toList()
                    );
                });

                return result;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            panicSender.sendPanic("Getting all h2h by current week error", e);
            serverLogger.error(e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static final class HeadToHeadMapper implements RowMapper<HeadToHead> {

        @Override
        public HeadToHead mapRow(ResultSet rs, int rowNum) throws SQLException {
            return HeadToHead.builder()
                    .homeTeamId(rs.getInt("home_team_id"))
                    .awayTeamId(rs.getInt("away_team_id"))
                    .homeScore(rs.getInt("home_score"))
                    .awayScore(rs.getInt("away_score"))
                    .leagueName(rs.getString("league_name"))
                    .startsAt(rs.getTimestamp("starts_at").toLocalDateTime())
                    .build();
        }
    }

    private static final class MatchHeadToHeadMapper implements RowMapper<MatchHeadToHead> {

        @Override
        public MatchHeadToHead mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MatchHeadToHead(
                    rs.getInt("id"),
                    HeadToHead.builder()
                            .homeTeamId(rs.getInt("home_team_id"))
                            .awayTeamId(rs.getInt("away_team_id"))
                            .homeScore(rs.getInt("home_score"))
                            .awayScore(rs.getInt("away_score"))
                            .leagueName(rs.getString("league_name"))
                            .startsAt(rs.getTimestamp("starts_at").toLocalDateTime())
                            .build()
            );
        }
    }

    private record MatchHeadToHead(int matchId, HeadToHead headToHead) {
    }
}
