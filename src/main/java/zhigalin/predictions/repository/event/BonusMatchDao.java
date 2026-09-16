package zhigalin.predictions.repository.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

@Repository
@DependsOn("bonusMatchesSchemaMigration")
public class BonusMatchDao {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public BonusMatchDao(DataSource dataSource, PanicSender panicSender) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public void upsert(BonusMatch match) {
        String sql = """
                INSERT INTO bonus_match (
                    public_id, competition, espn_id,
                    home_team_id, away_team_id,
                    home_name, away_name, home_logo_url, away_logo_url,
                    home_espn_code, away_espn_code,
                    home_team_score, away_team_score, result, status,
                    local_date_time, finished_at, live_score_message_id
                ) VALUES (
                    :publicId, :competition, :espnId,
                    :homeTeamId, :awayTeamId,
                    :homeName, :awayName, :homeLogoUrl, :awayLogoUrl,
                    :homeEspnCode, :awayEspnCode,
                    :homeScore, :awayScore, :result, :status,
                    :localDateTime, :finishedAt, :liveScoreMessageId
                )
                ON CONFLICT (competition, espn_id) DO UPDATE SET
                    public_id = EXCLUDED.public_id,
                    home_team_id = EXCLUDED.home_team_id,
                    away_team_id = EXCLUDED.away_team_id,
                    home_name = EXCLUDED.home_name,
                    away_name = EXCLUDED.away_name,
                    home_logo_url = EXCLUDED.home_logo_url,
                    away_logo_url = EXCLUDED.away_logo_url,
                    home_espn_code = EXCLUDED.home_espn_code,
                    away_espn_code = EXCLUDED.away_espn_code,
                    home_team_score = EXCLUDED.home_team_score,
                    away_team_score = EXCLUDED.away_team_score,
                    result = EXCLUDED.result,
                    status = EXCLUDED.status,
                    local_date_time = EXCLUDED.local_date_time,
                    finished_at = COALESCE(EXCLUDED.finished_at, bonus_match.finished_at),
                    live_score_message_id = COALESCE(EXCLUDED.live_score_message_id, bonus_match.live_score_message_id)
                """;
        try {
            namedParameterJdbcTemplate.update(sql, params(match));
        } catch (Exception e) {
            panicSender.sendPanic("Error upsert bonus_match", e);
            serverLogger.error(e.getMessage());
        }
    }

    public void update(BonusMatch match) {
        String sql = """
                UPDATE bonus_match SET
                    home_team_score = :homeScore,
                    away_team_score = :awayScore,
                    result = :result,
                    status = :status,
                    local_date_time = :localDateTime,
                    finished_at = :finishedAt,
                    live_score_message_id = :liveScoreMessageId,
                    home_team_id = :homeTeamId,
                    away_team_id = :awayTeamId,
                    home_name = :homeName,
                    away_name = :awayName,
                    home_logo_url = :homeLogoUrl,
                    away_logo_url = :awayLogoUrl,
                    home_espn_code = :homeEspnCode,
                    away_espn_code = :awayEspnCode
                WHERE public_id = :publicId
                """;
        try {
            namedParameterJdbcTemplate.update(sql, params(match));
        } catch (Exception e) {
            panicSender.sendPanic("Error update bonus_match", e);
            serverLogger.error(e.getMessage());
        }
    }

    public BonusMatch findByPublicId(int publicId) {
        try {
            String sql = "SELECT * FROM bonus_match WHERE public_id = :publicId";
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(
                    sql, new MapSqlParameterSource("publicId", publicId), new BonusMatchMapper()));
        } catch (Exception e) {
            panicSender.sendPanic("Error find bonus_match by id", e);
            return null;
        }
    }

    public BonusMatch findByCompetitionAndEspnId(String competition, String espnId) {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE competition = :competition AND espn_id = :espnId
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("competition", competition)
                    .addValue("espnId", espnId);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.queryForObject(
                    sql, params, new BonusMatchMapper()));
        } catch (Exception e) {
            return null;
        }
    }

    public List<BonusMatch> findAllByDate(LocalDate date) {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE CAST(local_date_time AS DATE) = :date
                    ORDER BY local_date_time, public_id
                    """;
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, new MapSqlParameterSource("date", date), new BonusMatchMapper()));
        } catch (Exception e) {
            panicSender.sendPanic("Error find bonus_match by date", e);
            return List.of();
        }
    }

    public List<BonusMatch> findOnline() {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE status IS NOT NULL
                      AND status NOT IN ('ns', 'ft', 'pst', 'aet', 'pen')
                    ORDER BY local_date_time, public_id
                    """;
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, new BonusMatchMapper()));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<BonusMatch> findByCompetition(String competition) {
        return findByCompetitionFrom(competition, LocalDate.now(zhigalin.predictions.util.AppTimeZones.DISPLAY));
    }

    public List<BonusMatch> findByCompetitionFrom(String competition, LocalDate fromDate) {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE competition = :competition
                      AND CAST(local_date_time AS DATE) >= :fromDate
                    ORDER BY local_date_time, public_id
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("competition", competition)
                    .addValue("fromDate", fromDate);
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, params, new BonusMatchMapper()));
        } catch (Exception e) {
            panicSender.sendPanic("Error find bonus_match by competition", e);
            return List.of();
        }
    }

    public List<BonusMatch> findAllOrdered() {
        return findAllFrom(LocalDate.now(zhigalin.predictions.util.AppTimeZones.DISPLAY));
    }

    public List<BonusMatch> findAllFrom(LocalDate fromDate) {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE CAST(local_date_time AS DATE) >= :fromDate
                    ORDER BY local_date_time, public_id
                    """;
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, new MapSqlParameterSource("fromDate", fromDate), new BonusMatchMapper()));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<BonusMatch> findFinished() {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE status = 'ft'
                    ORDER BY COALESCE(finished_at, local_date_time), public_id
                    """;
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, new BonusMatchMapper()));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<BonusMatch> findUpcoming(int withinMinutes) {
        try {
            String sql = """
                    SELECT * FROM bonus_match
                    WHERE status = 'ns'
                      AND local_date_time BETWEEN :from AND :to
                    ORDER BY local_date_time, public_id
                    """;
            LocalDateTime now = LocalDateTime.now();
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("from", now)
                    .addValue("to", now.plusMinutes(withinMinutes));
            return DaoUtil.getNullableResult(() -> namedParameterJdbcTemplate.query(
                    sql, params, new BonusMatchMapper()));
        } catch (Exception e) {
            return List.of();
        }
    }

    public void updateLiveScoreMessageId(int publicId, Integer messageId) {
        try {
            namedParameterJdbcTemplate.update(
                    "UPDATE bonus_match SET live_score_message_id = :mid WHERE public_id = :id",
                    new MapSqlParameterSource().addValue("mid", messageId).addValue("id", publicId));
        } catch (Exception e) {
            serverLogger.error("updateLiveScoreMessageId bonus: {}", e.getMessage());
        }
    }

    private static MapSqlParameterSource params(BonusMatch match) {
        return new MapSqlParameterSource()
                .addValue("publicId", match.getPublicId())
                .addValue("competition", match.getCompetition())
                .addValue("espnId", match.getEspnId())
                .addValue("homeTeamId", match.getHomeTeamId())
                .addValue("awayTeamId", match.getAwayTeamId())
                .addValue("homeName", match.getHomeName())
                .addValue("awayName", match.getAwayName())
                .addValue("homeLogoUrl", match.getHomeLogoUrl())
                .addValue("awayLogoUrl", match.getAwayLogoUrl())
                .addValue("homeEspnCode", match.getHomeEspnCode())
                .addValue("awayEspnCode", match.getAwayEspnCode())
                .addValue("homeScore", match.getHomeTeamScore())
                .addValue("awayScore", match.getAwayTeamScore())
                .addValue("result", match.getResult())
                .addValue("status", match.getStatus())
                .addValue("localDateTime", match.getLocalDateTime())
                .addValue("finishedAt", match.getFinishedAt())
                .addValue("liveScoreMessageId", match.getLiveScoreMessageId());
    }

    private static final class BonusMatchMapper implements RowMapper<BonusMatch> {
        @Override
        public BonusMatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp ldt = rs.getTimestamp("local_date_time");
            Timestamp fat = rs.getTimestamp("finished_at");
            return BonusMatch.builder()
                    .publicId(rs.getInt("public_id"))
                    .competition(rs.getString("competition"))
                    .espnId(rs.getString("espn_id"))
                    .homeTeamId((Integer) rs.getObject("home_team_id"))
                    .awayTeamId((Integer) rs.getObject("away_team_id"))
                    .homeName(rs.getString("home_name"))
                    .awayName(rs.getString("away_name"))
                    .homeLogoUrl(rs.getString("home_logo_url"))
                    .awayLogoUrl(rs.getString("away_logo_url"))
                    .homeEspnCode(rs.getString("home_espn_code"))
                    .awayEspnCode(rs.getString("away_espn_code"))
                    .homeTeamScore((Integer) rs.getObject("home_team_score"))
                    .awayTeamScore((Integer) rs.getObject("away_team_score"))
                    .result(rs.getString("result"))
                    .status(rs.getString("status"))
                    .localDateTime(ldt != null ? ldt.toLocalDateTime() : null)
                    .finishedAt(fat != null ? fat.toLocalDateTime() : null)
                    .liveScoreMessageId((Integer) rs.getObject("live_score_message_id"))
                    .build();
        }
    }
}
