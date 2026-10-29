package zhigalin.predictions.repository.event;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.recommender.CupOddsModel;

@Repository
@DependsOn("cupMatchesSchemaMigration")
public class CupMatchTipDao {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final PanicSender panicSender;

    public CupMatchTipDao(DataSource dataSource, PanicSender panicSender) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.panicSender = panicSender;
    }

    public record Tip(int cupMatchId, CupOddsModel.Tip tip, CupOddsModel.Odds odds, CupOddsModel.Form form,
                      LocalDateTime updatedAt) {
    }

    public void upsert(Tip row) {
        try {
            String sql = """
                    INSERT INTO cup_match_tip (
                        cup_match_id, home_score, away_score, expected_points, p_home, p_draw, p_away,
                        odd_home, odd_draw, odd_away, over_line, over_odds, under_odds, form_applied,
                        home_form_goals_for, home_form_goals_against, home_form_matches,
                        away_form_goals_for, away_form_goals_against, away_form_matches,
                        h2h_home_goals, h2h_away_goals, h2h_matches, updated_at
                    ) VALUES (
                        :cupMatchId, :home, :away, :expectedPoints, :pHome, :pDraw, :pAway,
                        :oddHome, :oddDraw, :oddAway, :overLine, :overOdds, :underOdds, :formApplied,
                        :homeGoalsFor, :homeGoalsAgainst, :homeMatches,
                        :awayGoalsFor, :awayGoalsAgainst, :awayMatches,
                        :h2hHomeGoals, :h2hAwayGoals, :h2hMatches, :updatedAt
                    )
                    ON CONFLICT (cup_match_id) DO UPDATE SET
                        home_score = EXCLUDED.home_score,
                        away_score = EXCLUDED.away_score,
                        expected_points = EXCLUDED.expected_points,
                        p_home = EXCLUDED.p_home,
                        p_draw = EXCLUDED.p_draw,
                        p_away = EXCLUDED.p_away,
                        odd_home = EXCLUDED.odd_home,
                        odd_draw = EXCLUDED.odd_draw,
                        odd_away = EXCLUDED.odd_away,
                        over_line = EXCLUDED.over_line,
                        over_odds = EXCLUDED.over_odds,
                        under_odds = EXCLUDED.under_odds,
                        form_applied = EXCLUDED.form_applied,
                        home_form_goals_for = EXCLUDED.home_form_goals_for,
                        home_form_goals_against = EXCLUDED.home_form_goals_against,
                        home_form_matches = EXCLUDED.home_form_matches,
                        away_form_goals_for = EXCLUDED.away_form_goals_for,
                        away_form_goals_against = EXCLUDED.away_form_goals_against,
                        away_form_matches = EXCLUDED.away_form_matches,
                        h2h_home_goals = EXCLUDED.h2h_home_goals,
                        h2h_away_goals = EXCLUDED.h2h_away_goals,
                        h2h_matches = EXCLUDED.h2h_matches,
                        updated_at = EXCLUDED.updated_at
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("cupMatchId", row.cupMatchId())
                    .addValue("home", row.tip().home())
                    .addValue("away", row.tip().away())
                    .addValue("expectedPoints", row.tip().expectedPoints())
                    .addValue("pHome", row.tip().pHome())
                    .addValue("pDraw", row.tip().pDraw())
                    .addValue("pAway", row.tip().pAway())
                    .addValue("oddHome", row.odds().home())
                    .addValue("oddDraw", row.odds().draw())
                    .addValue("oddAway", row.odds().away())
                    .addValue("overLine", row.odds().overLine())
                    .addValue("overOdds", row.odds().over())
                    .addValue("underOdds", row.odds().under())
                    .addValue("formApplied", row.tip().formApplied())
                    .addValue("homeGoalsFor", row.form().home() == null ? null : row.form().home().goalsFor())
                    .addValue("homeGoalsAgainst", row.form().home() == null ? null : row.form().home().goalsAgainst())
                    .addValue("homeMatches", row.form().home() == null ? null : row.form().home().matches())
                    .addValue("awayGoalsFor", row.form().away() == null ? null : row.form().away().goalsFor())
                    .addValue("awayGoalsAgainst", row.form().away() == null ? null : row.form().away().goalsAgainst())
                    .addValue("awayMatches", row.form().away() == null ? null : row.form().away().matches())
                    .addValue("h2hHomeGoals", row.form().h2h() == null ? null : row.form().h2h().homeGoals())
                    .addValue("h2hAwayGoals", row.form().h2h() == null ? null : row.form().h2h().awayGoals())
                    .addValue("h2hMatches", row.form().h2h() == null ? null : row.form().h2h().matches())
                    .addValue("updatedAt", row.updatedAt());
            namedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            serverLogger.error("upsert cup_match_tip {}: {}", row.cupMatchId(), e.getMessage());
            panicSender.sendPanic("Error upsert cup_match_tip", e);
        }
    }

    public Map<Integer, CupOddsModel.Odds> oddsByCupMatchIds(Collection<Integer> cupMatchIds) {
        Map<Integer, CupOddsModel.Odds> result = new HashMap<>();
        if (cupMatchIds == null || cupMatchIds.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT cup_match_id, odd_home, odd_draw, odd_away, over_line, over_odds, under_odds
                FROM cup_match_tip WHERE cup_match_id IN (:ids)
                """;
        namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource("ids", cupMatchIds), rs -> {
            if (rs.getObject("odd_home") == null) {
                return;
            }
            result.put(rs.getInt("cup_match_id"), new CupOddsModel.Odds(
                    rs.getDouble("odd_home"), rs.getDouble("odd_draw"), rs.getDouble("odd_away"),
                    (Double) rs.getObject("over_line"), (Double) rs.getObject("over_odds"),
                    (Double) rs.getObject("under_odds")));
        });
        return result;
    }

    public Optional<CupOddsModel.Tip> findByCupMatchId(int cupMatchId) {
        String sql = """
                SELECT home_score, away_score, expected_points, p_home, p_draw, p_away, form_applied
                FROM cup_match_tip WHERE cup_match_id = :cupMatchId
                """;
        return namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource("cupMatchId", cupMatchId), rs -> rs.next()
                ? Optional.of(new CupOddsModel.Tip(rs.getInt("home_score"), rs.getInt("away_score"),
                rs.getDouble("expected_points"), rs.getDouble("p_home"), rs.getDouble("p_draw"),
                rs.getDouble("p_away"), rs.getBoolean("form_applied")))
                : Optional.empty());
    }

    public List<int[]> findFinishedTipResults() {
        String sql = """
                SELECT t.home_score, t.away_score, c.home_team_score, c.away_team_score
                FROM cup_match_tip t
                JOIN cup_match c ON c.public_id = t.cup_match_id
                WHERE c.status = 'ft' AND c.home_team_score IS NOT NULL AND c.away_team_score IS NOT NULL
                """;
        return namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new int[]{
                rs.getInt("home_score"), rs.getInt("away_score"),
                rs.getInt("home_team_score"), rs.getInt("away_team_score")});
    }

    public Map<Integer, LocalDateTime> updatedAtByCupMatchIds(Collection<Integer> cupMatchIds) {
        Map<Integer, LocalDateTime> result = new HashMap<>();
        if (cupMatchIds == null || cupMatchIds.isEmpty()) {
            return result;
        }
        String sql = "SELECT cup_match_id, updated_at FROM cup_match_tip WHERE cup_match_id IN (:ids)";
        namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource("ids", cupMatchIds), rs -> {
            result.put(rs.getInt("cup_match_id"), rs.getTimestamp("updated_at").toLocalDateTime());
        });
        return result;
    }

    public Map<Integer, int[]> kickoffScoresByCupMatchIds(Collection<Integer> cupMatchIds) {
        Map<Integer, int[]> result = new HashMap<>();
        if (cupMatchIds == null || cupMatchIds.isEmpty()) {
            return result;
        }
        String sql = "SELECT cup_match_id, home_score, away_score FROM cup_match_tip WHERE cup_match_id IN (:ids)";
        namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource("ids", cupMatchIds), rs -> {
            result.put(rs.getInt("cup_match_id"), new int[]{rs.getInt("home_score"), rs.getInt("away_score")});
        });
        return result;
    }
}
