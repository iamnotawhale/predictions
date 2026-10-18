#!/usr/bin/env bash
set -uo pipefail

STATE=$HOME/.local/state/predicts-build
SRC=$HOME/predictions-src
APP=$HOME/predictions
JAR=$APP/target/predictions-1.0.0.jar
HEALTH=http://127.0.0.1:8080/miniapp/
ENV_FILE=$APP/deploy/predicts.env
LEAGUES="eng.1 eng.fa eng.league_cup uefa.champions uefa.europa uefa.europa.conf"
mkdir -p "$STATE"

log() { echo "$(date -Iseconds) [server-deploy] $*"; }
status() { echo "$1" > "$STATE/status"; log "status=$1"; }

env_value() { sed -n "s/^$1=//p" "$ENV_FILE" | head -n1 | sed -e 's/^"//' -e 's/"$//'; }

alert() {
  local token chat
  token=$(env_value BOT_TOKEN); chat=$(env_value ADMIN_CHAT_ID)
  [ -n "$token" ] && [ -n "$chat" ] || return 0
  curl -s -m 15 "https://api.telegram.org/bot${token}/sendMessage" \
    --data-urlencode "chat_id=${chat}" --data-urlencode "text=[deploy] $1" >/dev/null || true
}

live_matches() {
  local league
  for league in $LEAGUES; do
    curl -s -m 20 -A predictions-bot/1.0 \
      "https://site.web.api.espn.com/apis/site/v2/sports/soccer/${league}/scoreboard" |
      python3 -c 'import json,sys; sys.exit(0 if any(e["status"]["type"]["state"]=="in" for e in json.load(sys.stdin).get("events",[])) else 1)' 2>/dev/null && return 0
  done
  return 1
}

healthy() {
  local i
  for i in $(seq 1 30); do
    if systemctl is-active --quiet predicts && curl -sf -o /dev/null -m 5 "$HEALTH"; then
      return 0
    fi
    sleep 3
  done
  return 1
}

exec 9>"$STATE/lock"
flock -n 9 || { log "another deploy is running"; exit 0; }

while true; do
  sha=$(cat "$STATE/requested" 2>/dev/null || true)
  [ -n "$sha" ] || { status idle; exit 0; }
  if [ "$sha" = "$(cat "$STATE/installed" 2>/dev/null || true)" ]; then
    status idle; log "$sha already installed"; exit 0
  fi

  while live_matches; do
    status waiting
    sleep 120
  done

  status building
  git -C "$SRC" fetch -q origin master || { echo "$sha" > "$STATE/failed"; alert "fetch failed"; status failed; exit 1; }
  if ! git -C "$SRC" merge-base --is-ancestor "$sha" origin/master; then
    echo "$sha" > "$STATE/failed"; alert "sha $sha is not in master"; status failed; exit 1
  fi
  git -C "$SRC" checkout -q --detach "$sha" || { echo "$sha" > "$STATE/failed"; status failed; exit 1; }

  export JAVA_HOME=$HOME/jdk/jdk-21.0.12.1+1
  export PATH=$JAVA_HOME/bin:$PATH
  export MAVEN_OPTS=-Xmx1g
  log "building $sha"
  if ! (cd "$SRC" && nice -n 19 ionice -c3 bash ./mvnw -B -q -DskipTests package > "$STATE/build.log" 2>&1); then
    echo "$sha" > "$STATE/failed"; alert "build failed for $sha, prod untouched"; status failed; exit 1
  fi
  if ! unzip -tq "$SRC/target/predictions-1.0.0.jar" >/dev/null; then
    echo "$sha" > "$STATE/failed"; alert "bad jar for $sha"; status failed; exit 1
  fi
  cp -f "$SRC/target/predictions-1.0.0.jar" "$JAR.new"

  status waiting
  while live_matches; do
    status waiting
    sleep 120
  done

  status installing
  log "installing $sha"
  cp -a "$JAR" "$JAR.bak" 2>/dev/null || true
  sudo -n systemctl stop predicts || true
  mv -f "$JAR.new" "$JAR"
  ts=$(date +%s)
  sudo -n systemctl start predicts || true
  if healthy && journalctl -u predicts --since "@$ts" --no-pager | grep -q "Started PredictionsApplication"; then
    echo "$sha" > "$STATE/installed"; rm -f "$STATE/failed"
    log "installed $sha"
    if [ "$(cat "$STATE/requested" 2>/dev/null || true)" != "$sha" ]; then
      continue
    fi
    status idle
    exit 0
  fi

  log "health check failed, rolling back"
  echo "$sha" > "$STATE/failed"
  sudo -n systemctl stop predicts || true
  cp -a "$JAR.bak" "$JAR" 2>/dev/null || true
  ts=$(date +%s)
  sudo -n systemctl start predicts || true
  if healthy; then
    alert "deploy $sha failed health check, rolled back and healthy"
  else
    alert "deploy $sha failed and rollback is NOT healthy: prod is DOWN"
  fi
  status failed
  exit 1
done
