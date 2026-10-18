#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-deploy}"
DEPLOY_ENV="${DEPLOY_ENV:-$(cd "$(dirname "$0")" && pwd)/pull-deploy.env}"
if [ ! -r "$DEPLOY_ENV" ]; then
  echo "missing $DEPLOY_ENV" >&2
  exit 2
fi
set -a
. "$DEPLOY_ENV"
set +a

: "${GH_TOKEN:?}" "${GH_REPO:?}" "${GH_WORKFLOW:?}" "${GH_ARTIFACT:?}" "${APP_DIR:?}" "${HEALTH_URL:?}" "${PREDICTS_ENV:?}"
STATE_DIR="${STATE_DIR:-$HOME/.local/state/predicts-deploy}"
JAR_NAME="predictions-1.0.0.jar"
JAR="$APP_DIR/target/$JAR_NAME"
mkdir -p "$STATE_DIR"

log() { echo "$(date -Iseconds) [pull-deploy] $*" >&2; }

env_value() {
  sed -n "s/^$1=//p" "$PREDICTS_ENV" | head -n1 | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"
}

alert_once() {
  local key="$1" msg="$2" token chat
  if [ "$(cat "$STATE_DIR/last_alert" 2>/dev/null || true)" = "$key" ]; then
    return 0
  fi
  echo "$key" > "$STATE_DIR/last_alert"
  log "alert: $key"
  token="$(env_value BOT_TOKEN)"
  chat="$(env_value ADMIN_CHAT_ID)"
  [ -n "$token" ] && [ -n "$chat" ] || return 0
  curl -s -m 15 "https://api.telegram.org/bot${token}/sendMessage" \
    --data-urlencode "chat_id=${chat}" \
    --data-urlencode "text=[pull-deploy] ${msg}" >/dev/null || true
}

on_error() {
  local code=$?
  [ "$code" -eq 3 ] && exit 3
  log "failed with exit $code"
  alert_once "error-$code-$(date +%F)" "unexpected failure (exit $code), see journalctl -u predicts-deploy"
  exit "$code"
}
trap on_error ERR

api() {
  curl -sS -m 30 -w '\n%{http_code}' \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "$1"
}

api_json() {
  local out code body
  out="$(api "$1")"
  code="${out##*$'\n'}"
  body="${out%$'\n'*}"
  if [ "$code" = "401" ] || [ "$code" = "403" ]; then
    log "GitHub API auth failed (HTTP $code): token expired or lacks Actions read"
    alert_once "auth-$code" "GitHub API HTTP $code: token expired or lacks Actions read"
    exit 3
  fi
  if [ "$code" != "200" ]; then
    log "GitHub API HTTP $code for $1"
    exit 1
  fi
  printf '%s' "$body"
}

wait_healthy() {
  local i
  for i in $(seq 1 30); do
    if systemctl is-active --quiet predicts && curl -sf -o /dev/null "$HEALTH_URL"; then
      log "healthy after ${i} checks"
      return 0
    fi
    sleep 3
  done
  return 1
}

preflight() {
  sudo -n -l systemctl start predicts >/dev/null 2>&1 && sudo -n -l systemctl stop predicts >/dev/null 2>&1 || {
    log "sudo without password for systemctl stop/start predicts is not available"
    alert_once "sudo-denied" "sudo for systemctl stop/start predicts is not available, deploy aborted before touching prod"
    exit 1
  }
}

exec 9>"$STATE_DIR/lock"
if ! flock -n 9; then
  log "another run in progress"
  exit 0
fi

if [ "$MODE" = "--reinstall-current" ]; then
  test -s "$JAR"
  preflight
  sudo systemctl stop predicts 2>/dev/null || true
  sudo systemctl reset-failed predicts 2>/dev/null || true
  sudo systemctl start predicts || true
  if wait_healthy; then
    log "reinstall-current ok"
    exit 0
  fi
  alert_once "reinstall-unhealthy" "reinstall-current: predicts is not healthy"
  exit 1
fi

RUNS="$(api_json "https://api.github.com/repos/${GH_REPO}/actions/workflows/${GH_WORKFLOW}/runs?branch=master&event=push&status=success&per_page=10")"
RUN_LINE="$(printf '%s' "$RUNS" | GH_REPO="$GH_REPO" python3 -c '
import json, os, sys
for r in json.load(sys.stdin)["workflow_runs"]:
    if r["head_repository"]["full_name"] == os.environ["GH_REPO"]:
        print(r["id"], r["head_sha"])
        break
')"
if [ -z "$RUN_LINE" ]; then
  log "no successful push run on master yet"
  exit 0
fi
RUN_ID="${RUN_LINE%% *}"
RUN_SHA="${RUN_LINE#* }"

INSTALLED="$(cat "$STATE_DIR/installed_run" 2>/dev/null || true)"
FAILED="$(cat "$STATE_DIR/failed_run" 2>/dev/null || true)"
if [ "$RUN_ID" = "$INSTALLED" ]; then
  log "run $RUN_ID already installed"
  exit 0
fi
if [ "$RUN_ID" = "$FAILED" ]; then
  log "run $RUN_ID previously failed the health check, waiting for a new commit"
  exit 0
fi

ARTIFACTS="$(api_json "https://api.github.com/repos/${GH_REPO}/actions/runs/${RUN_ID}/artifacts")"
ARCHIVE_URL="$(printf '%s' "$ARTIFACTS" | GH_ARTIFACT="$GH_ARTIFACT" python3 -c '
import json, os, sys
for a in json.load(sys.stdin)["artifacts"]:
    if a["name"] == os.environ["GH_ARTIFACT"] and not a["expired"]:
        print(a["archive_download_url"])
        break
')"
if [ -z "$ARCHIVE_URL" ]; then
  log "artifact $GH_ARTIFACT missing or expired in run $RUN_ID"
  alert_once "artifact-$RUN_ID" "artifact missing or expired in run $RUN_ID"
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -sSfL -m 600 -H "Authorization: Bearer ${GH_TOKEN}" -o "$TMP/artifact.zip" "$ARCHIVE_URL"
unzip -tq "$TMP/artifact.zip" >/dev/null
unzip -q "$TMP/artifact.zip" -d "$TMP/out"
NEW_JAR="$TMP/out/$JAR_NAME"
test -s "$NEW_JAR"

if [ "$MODE" = "--dry-run" ]; then
  log "dry-run: would install run $RUN_ID (sha ${RUN_SHA:0:7}, $(stat -c %s "$NEW_JAR") bytes)"
  exit 0
fi

log "installing run $RUN_ID (sha ${RUN_SHA:0:7})"
preflight
install -m 644 "$NEW_JAR" "$JAR.new"
sudo systemctl stop predicts 2>/dev/null || true
if [ -f "$JAR" ]; then
  cp -a "$JAR" "$JAR.bak"
fi
mv -f "$JAR.new" "$JAR"
sudo systemctl reset-failed predicts 2>/dev/null || true
sudo systemctl start predicts || true

if wait_healthy; then
  echo "$RUN_ID" > "$STATE_DIR/installed_run"
  rm -f "$STATE_DIR/failed_run" "$STATE_DIR/last_alert"
  log "installed run $RUN_ID"
  exit 0
fi

log "health check failed, rolling back"
echo "$RUN_ID" > "$STATE_DIR/failed_run"
if [ ! -f "$JAR.bak" ]; then
  alert_once "no-backup-$RUN_ID" "run $RUN_ID failed health check and there is no backup, prod is down"
  exit 1
fi
sudo systemctl stop predicts 2>/dev/null || true
cp -a "$JAR.bak" "$JAR"
sudo systemctl start predicts || true
if wait_healthy; then
  alert_once "rolled-back-$RUN_ID" "run $RUN_ID failed health check, rolled back and healthy"
  exit 1
fi
alert_once "rollback-unhealthy-$RUN_ID" "run $RUN_ID failed and the rollback is also unhealthy, prod is DOWN"
exit 1
