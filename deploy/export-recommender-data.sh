#!/usr/bin/env bash
# Exports the data needed to back-test the betting recommender into ./recommender-export/*.csv
# (matches + odds + stored tips, FootyStats team stats, league snapshot, h2h history, optional anonymous picks).
# No users, logins, tokens or passwords are exported.
#
#   PGPASSWORD=… ./deploy/export-recommender-data.sh            # host/user/db default to the prod box
#   PGHOST=127.0.0.1 PGUSER=admin PGDATABASE=predicts_prod PGPASSWORD=… ./deploy/export-recommender-data.sh
#   PSQL=echo ./deploy/export-recommender-data.sh                # dry run: only print the commands
set -euo pipefail

PSQL="${PSQL:-psql}"
export PGHOST="${PGHOST:-127.0.0.1}" PGUSER="${PGUSER:-admin}" PGDATABASE="${PGDATABASE:-predicts_prod}"
OUT="${OUT:-recommender-export}"
mkdir -p "$OUT"

# psql's \copy must be ONE line, so collapse the SQL's whitespace first.
export_csv() {
  local name="$1" sql
  sql="$(echo "$2" | tr '\n' ' ' | tr -s ' ')"
  $PSQL -v ON_ERROR_STOP=1 -c "\\copy ($sql) TO '$OUT/$name.csv' CSV HEADER"
  echo "wrote $OUT/$name.csv"
}

export_csv matches "
SELECT m.public_id, m.week_id, th.code AS home, ta.code AS away,
       m.home_team_score AS fh, m.away_team_score AS fa, m.status, m.local_date_time,
       m.odd_home, m.odd_draw, m.odd_away, m.odd_over_under,
       m.odd_home_team_total, m.odd_away_team_total,
       mr.recommended_home, mr.recommended_away, mr.expected_home_goals, mr.expected_away_goals,
       mr.score_probability, mr.kickoff_home, mr.kickoff_away, mr.kickoff_frozen_at, mr.explanation_json
FROM match m
JOIN teams th ON th.public_id = m.home_team_id
JOIN teams ta ON ta.public_id = m.away_team_id
LEFT JOIN match_recommendation mr ON mr.match_public_id = m.public_id
WHERE m.home_team_score IS NOT NULL
ORDER BY m.week_id, m.local_date_time"

export_csv team_stats "SELECT * FROM footystats_team_stats ORDER BY week_id, team_code"
export_csv league "SELECT * FROM footystats_league_snapshot ORDER BY week_id"

export_csv h2h "
SELECT th.code AS home, ta.code AS away, h.home_team_score, h.away_team_score, h.league_name, h.local_date_time
FROM h2h h
JOIN teams th ON th.public_id = h.home_team_id
JOIN teams ta ON ta.public_id = h.away_team_id
ORDER BY h.local_date_time"

# Optional: how people picked (numeric user ids only) — a human baseline for the AI.
export_csv picks "SELECT match_id, user_id, home_team_score, away_team_score, points FROM predict"

echo "done -> $OUT/ (put these in a scratch branch, not master)"
