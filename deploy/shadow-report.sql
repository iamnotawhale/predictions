-- Shadow mode report: final recommender tip (stats-shaped) vs pure-market tip, EPL points (4/2/1/-1).
-- Usage: psql "$DATABASE_URL" -f deploy/shadow-report.sql
WITH t AS (
    SELECT m.public_id,
           m.home_team_score AS rh, m.away_team_score AS ra,
           COALESCE(mr.kickoff_home, mr.recommended_home) AS fh,
           COALESCE(mr.kickoff_away, mr.recommended_away) AS fa,
           mr.shadow_home AS sh, mr.shadow_away AS sa
    FROM match m
    JOIN match_recommendation mr ON mr.match_public_id = m.public_id
    WHERE m.status IN ('ft') AND m.home_team_score IS NOT NULL
      AND mr.shadow_home IS NOT NULL
), p AS (
    SELECT *,
        CASE WHEN fh = rh AND fa = ra THEN 4 WHEN fh - fa = rh - ra THEN 2
             WHEN sign(fh - fa) = sign(rh - ra) THEN 1 ELSE -1 END AS final_pts,
        CASE WHEN sh = rh AND sa = ra THEN 4 WHEN sh - sa = rh - ra THEN 2
             WHEN sign(sh - sa) = sign(rh - ra) THEN 1 ELSE -1 END AS shadow_pts
    FROM t
)
SELECT COUNT(*) AS matches,
       ROUND(AVG(final_pts)::numeric, 3) AS final_avg_pts,
       ROUND(AVG(shadow_pts)::numeric, 3) AS market_only_avg_pts,
       ROUND(AVG(final_pts - shadow_pts)::numeric, 3) AS diff,
       ROUND((STDDEV(final_pts - shadow_pts) / SQRT(NULLIF(COUNT(*), 0)))::numeric, 3) AS diff_stderr,
       COUNT(*) FILTER (WHERE (fh, fa) <> (sh, sa)) AS tips_that_differ
FROM p;
