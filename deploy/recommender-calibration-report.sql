-- Recommender calibration report (read-only).
-- Prefer kickoff freeze when present; else fall back to live recommended_*.
--
-- Run on Odyssey:
--   PGPASSWORD=… psql -h 127.0.0.1 -U admin -d predicts_prod -f deploy/recommender-calibration-report.sql

\echo === 1. Overall (finished matches with a tip) ===
WITH tips AS (
  SELECT
    m.public_id,
    m.week_id,
    th.code AS home,
    ta.code AS away,
    m.home_team_score AS fh,
    m.away_team_score AS fa,
    COALESCE(mr.kickoff_home, mr.recommended_home) AS rh,
    COALESCE(mr.kickoff_away, mr.recommended_away) AS ra,
    mr.expected_home_goals AS lh,
    mr.expected_away_goals AS la,
    mr.score_probability AS tip_p,
    (mr.kickoff_frozen_at IS NOT NULL) AS frozen
  FROM match_recommendation mr
  JOIN match m ON m.public_id = mr.match_public_id
  JOIN teams th ON th.public_id = m.home_team_id
  JOIN teams ta ON ta.public_id = m.away_team_id
  WHERE lower(m.status) IN ('ft', 'aet', 'pen')
    AND m.home_team_score IS NOT NULL
    AND m.away_team_score IS NOT NULL
)
SELECT
  count(*) AS n,
  round(100.0 * avg((fh = rh AND fa = ra)::int), 1) AS exact_pct,
  round(100.0 * avg((
      (fh > fa AND rh > ra) OR (fh < fa AND rh < ra) OR (fh = fa AND rh = ra)
    )::int), 1) AS outcome_pct,
  round(100.0 * avg((
      (fh - fa) = (rh - ra)
    )::int), 1) AS goal_diff_pct,
  round(avg(abs(fh - rh) + abs(fa - ra))::numeric, 2) AS mae_goals_sum,
  round(avg((fh + fa) - (lh + la))::numeric, 2) AS bias_total_goals_fact_minus_lambda,
  round(avg((fh - rh))::numeric, 2) AS bias_home_goals_fact_minus_tip,
  round(avg((fa - ra))::numeric, 2) AS bias_away_goals_fact_minus_tip,
  round(avg(tip_p)::numeric, 3) AS avg_tip_probability,
  count(*) FILTER (WHERE frozen) AS n_frozen
FROM tips;

\echo === 2. By week ===
WITH tips AS (
  SELECT
    m.week_id,
    m.home_team_score AS fh,
    m.away_team_score AS fa,
    COALESCE(mr.kickoff_home, mr.recommended_home) AS rh,
    COALESCE(mr.kickoff_away, mr.recommended_away) AS ra,
    mr.expected_home_goals AS lh,
    mr.expected_away_goals AS la
  FROM match_recommendation mr
  JOIN match m ON m.public_id = mr.match_public_id
  WHERE lower(m.status) IN ('ft', 'aet', 'pen')
    AND m.home_team_score IS NOT NULL
)
SELECT
  week_id,
  count(*) AS n,
  round(100.0 * avg((fh = rh AND fa = ra)::int), 1) AS exact_pct,
  round(100.0 * avg((
      (fh > fa AND rh > ra) OR (fh < fa AND rh < ra) OR (fh = fa AND rh = ra)
    )::int), 1) AS outcome_pct,
  round(avg(abs(fh - rh) + abs(fa - ra))::numeric, 2) AS mae,
  round(avg((fh + fa) - (lh + la))::numeric, 2) AS bias_total
FROM tips
GROUP BY week_id
ORDER BY week_id;

\echo === 3. Tip shape vs reality (draws / 1-0 family) ===
WITH tips AS (
  SELECT
    m.home_team_score AS fh,
    m.away_team_score AS fa,
    COALESCE(mr.kickoff_home, mr.recommended_home) AS rh,
    COALESCE(mr.kickoff_away, mr.recommended_away) AS ra
  FROM match_recommendation mr
  JOIN match m ON m.public_id = mr.match_public_id
  WHERE lower(m.status) IN ('ft', 'aet', 'pen')
    AND m.home_team_score IS NOT NULL
)
SELECT
  round(100.0 * avg((rh = ra)::int), 1) AS tip_draw_pct,
  round(100.0 * avg((fh = fa)::int), 1) AS fact_draw_pct,
  round(100.0 * avg((rh + ra <= 1)::int), 1) AS tip_le1_total_pct,
  round(100.0 * avg((fh + fa <= 1)::int), 1) AS fact_le1_total_pct,
  round(100.0 * avg((rh + ra >= 3)::int), 1) AS tip_ge3_total_pct,
  round(100.0 * avg((fh + fa >= 3)::int), 1) AS fact_ge3_total_pct,
  round(100.0 * avg(((rh = 1 AND ra = 0) OR (rh = 0 AND ra = 1) OR (rh = 1 AND ra = 1))::int), 1) AS tip_101_011_pct,
  round(100.0 * avg(((fh = 1 AND fa = 0) OR (fh = 0 AND fa = 1) OR (fh = 1 AND fa = 1))::int), 1) AS fact_101_011_pct
FROM tips;

\echo === 4. Favourite / underdog / even (by tip score) ===
WITH tips AS (
  SELECT
    m.home_team_score AS fh,
    m.away_team_score AS fa,
    COALESCE(mr.kickoff_home, mr.recommended_home) AS rh,
    COALESCE(mr.kickoff_away, mr.recommended_away) AS ra,
    CASE
      WHEN COALESCE(mr.kickoff_home, mr.recommended_home)
         > COALESCE(mr.kickoff_away, mr.recommended_away) THEN 'tip_home'
      WHEN COALESCE(mr.kickoff_home, mr.recommended_home)
         < COALESCE(mr.kickoff_away, mr.recommended_away) THEN 'tip_away'
      ELSE 'tip_draw'
    END AS tip_bucket
  FROM match_recommendation mr
  JOIN match m ON m.public_id = mr.match_public_id
  WHERE lower(m.status) IN ('ft', 'aet', 'pen')
    AND m.home_team_score IS NOT NULL
)
SELECT
  tip_bucket,
  count(*) AS n,
  round(100.0 * avg((fh = rh AND fa = ra)::int), 1) AS exact_pct,
  round(100.0 * avg((
      (fh > fa AND rh > ra) OR (fh < fa AND rh < ra) OR (fh = fa AND rh = ra)
    )::int), 1) AS outcome_pct,
  round(avg(abs(fh - rh) + abs(fa - ra))::numeric, 2) AS mae
FROM tips
GROUP BY tip_bucket
ORDER BY tip_bucket;

\echo === 5. Per-match detail (latest first) ===
SELECT
  m.week_id,
  th.code || '-' || ta.code AS fixture,
  m.home_team_score || ':' || m.away_team_score AS fact,
  COALESCE(mr.kickoff_home, mr.recommended_home) || ':' ||
    COALESCE(mr.kickoff_away, mr.recommended_away) AS tip,
  round(mr.expected_home_goals::numeric, 2) AS lambda_h,
  round(mr.expected_away_goals::numeric, 2) AS lambda_a,
  round(mr.score_probability::numeric, 3) AS tip_p,
  CASE WHEN mr.kickoff_frozen_at IS NOT NULL THEN 'Y' ELSE 'n' END AS frozen,
  CASE
    WHEN m.home_team_score = COALESCE(mr.kickoff_home, mr.recommended_home)
     AND m.away_team_score = COALESCE(mr.kickoff_away, mr.recommended_away) THEN 'exact'
    WHEN (m.home_team_score - m.away_team_score)
       = (COALESCE(mr.kickoff_home, mr.recommended_home) - COALESCE(mr.kickoff_away, mr.recommended_away))
      THEN 'diff'
    WHEN (m.home_team_score > m.away_team_score
          AND COALESCE(mr.kickoff_home, mr.recommended_home) > COALESCE(mr.kickoff_away, mr.recommended_away))
      OR (m.home_team_score < m.away_team_score
          AND COALESCE(mr.kickoff_home, mr.recommended_home) < COALESCE(mr.kickoff_away, mr.recommended_away))
      OR (m.home_team_score = m.away_team_score
          AND COALESCE(mr.kickoff_home, mr.recommended_home) = COALESCE(mr.kickoff_away, mr.recommended_away))
      THEN '1x2'
    ELSE 'miss'
  END AS hit
FROM match_recommendation mr
JOIN match m ON m.public_id = mr.match_public_id
JOIN teams th ON th.public_id = m.home_team_id
JOIN teams ta ON ta.public_id = m.away_team_id
WHERE lower(m.status) IN ('ft', 'aet', 'pen')
  AND m.home_team_score IS NOT NULL
ORDER BY m.local_date_time DESC, m.public_id DESC;

\echo === 6. Coverage gaps (finished week matches without tip) ===
SELECT
  m.week_id,
  th.code || '-' || ta.code AS fixture,
  m.status,
  m.home_team_score || ':' || m.away_team_score AS fact
FROM match m
JOIN teams th ON th.public_id = m.home_team_id
JOIN teams ta ON ta.public_id = m.away_team_id
WHERE lower(m.status) IN ('ft', 'aet', 'pen')
  AND NOT EXISTS (
    SELECT 1 FROM match_recommendation mr WHERE mr.match_public_id = m.public_id
  )
ORDER BY m.week_id, m.local_date_time;
