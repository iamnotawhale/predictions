#!/bin/bash
# Опрос API-Football: когда появятся составы (startXI) для матча.
# Использование:
#   ./scripts/poll-lineups.sh [fixture_id]
#   ./scripts/poll-lineups.sh          # сегодняшний домашний MUN из БД (prod)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="${APP_DIR:-$ROOT}"
ENV_FILE="${ENV_FILE:-$APP_DIR/deploy/predicts.env}"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1091
    source "$ENV_FILE"
fi

: "${API_FOOTBALL_TOKEN:?API_FOOTBALL_TOKEN not set (deploy/predicts.env)}"

FIXTURE_ID="${1:-}"
if [[ -z "$FIXTURE_ID" ]]; then
    DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/predicts_prod}"
    DB_USER="${SPRING_DATASOURCE_USERNAME:-admin}"
    DB_PASS="${SPRING_DATASOURCE_PASSWORD:-postgres}"
    DB_NAME="${DB_URL##*/}"
    FIXTURE_ID=$(PGPASSWORD="$DB_PASS" psql -h localhost -U "$DB_USER" -d "$DB_NAME" -t -A -c "
        SELECT m.public_id
        FROM match m
        JOIN teams ht ON ht.public_id = m.home_team_id
        WHERE ht.code = 'MUN'
          AND CAST(m.local_date_time AS DATE) = CURRENT_DATE
          AND m.status = 'ns'
        ORDER BY m.local_date_time
        LIMIT 1;
    " 2>/dev/null | tr -d '[:space:]')
fi

if [[ -z "$FIXTURE_ID" ]]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S %Z') ERROR: no fixture id (arg or today's MUN home match in DB)" >&2
    exit 1
fi

LOG_DIR="${LOG_DIR:-$APP_DIR/logs}"
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_FILE:-$LOG_DIR/lineups-poll-${FIXTURE_ID}.log}"

NOW="$(TZ=Europe/Moscow date '+%Y-%m-%d %H:%M:%S %Z')"
BODY=$(curl -sS --max-time 25 \
    "https://v3.football.api-sports.io/fixtures/lineups?fixture=${FIXTURE_ID}&type=startXI" \
    -H "x-rapidapi-key: ${API_FOOTBALL_TOKEN}" \
    -H "x-rapidapi-host: v3.football.api-sports.io")

LINE=$(printf '%s' "$BODY" | python3 -c "
import json, sys
from datetime import datetime
try:
    d = json.load(sys.stdin)
except json.JSONDecodeError as e:
    print(f'parse_error={e}')
    sys.exit(0)
errors = d.get('errors') or {}
results = d.get('results', 0)
teams = []
for r in d.get('response', []):
    t = r.get('team', {})
    n = len(r.get('startXI') or [])
    teams.append(f\"{t.get('id')}:{t.get('name','?')}={n}\")
teams_s = ','.join(teams) if teams else 'none'
err_s = json.dumps(errors) if errors else 'ok'
print(f'results={results} teams=[{teams_s}] errors={err_s}')
")

echo "${NOW} fixture=${FIXTURE_ID} ${LINE}" >> "$LOG_FILE"
echo "${NOW} fixture=${FIXTURE_ID} ${LINE}"
