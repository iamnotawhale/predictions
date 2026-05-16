#!/bin/bash
# Сборка локально + загрузка jar на сервер (без mvn на сервере — не вешает 1 ГБ RAM).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SERVER="${DEPLOY_SERVER:-root@146.255.188.80}"
APP_DIR="${DEPLOY_APP_DIR:-/home/predictions}"
JAR="target/predictions-1.0.0.jar"

log() { echo "[upload-jar] $*"; }

log "Local Maven build..."
mvn -q -DskipTests package

[[ -f "$JAR" ]] || { log "ERROR: $JAR not found"; exit 1; }
size=$(stat -c%s "$JAR" 2>/dev/null || stat -f%z "$JAR")
[[ "$size" -gt 1000000 ]] || { log "ERROR: jar too small ($size bytes)"; exit 1; }

log "Upload to $SERVER..."
ssh "$SERVER" "systemctl stop predicts 2>/dev/null || true; mkdir -p $APP_DIR/target"
if ssh "$SERVER" "test -f $APP_DIR/target/predictions-1.0.0.jar"; then
    ssh "$SERVER" "cp -a $APP_DIR/target/predictions-1.0.0.jar $APP_DIR/target/predictions-1.0.0.jar.bak"
fi
scp "$JAR" "$SERVER:$APP_DIR/target/predictions-1.0.0.jar.new"
ssh "$SERVER" "mv -f $APP_DIR/target/predictions-1.0.0.jar.new $APP_DIR/target/predictions-1.0.0.jar && jar tf $APP_DIR/target/predictions-1.0.0.jar >/dev/null"

log "Git pull + restart..."
ssh "$SERVER" "cd $APP_DIR && git pull origin v2-lite && install -m 644 deploy/predicts.service /etc/systemd/system/predicts.service && systemctl daemon-reload && systemctl reset-failed predicts 2>/dev/null; systemctl start predicts && sleep 6 && systemctl is-active predicts"

log "Done."
