#!/bin/bash
# Сборка локально + загрузка jar на прод (без mvn на сервере).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f deploy/deploy.env ]]; then
    # shellcheck disable=SC1091
    source deploy/deploy.env
fi

: "${DEPLOY_SERVER:?Задайте DEPLOY_SERVER (export или deploy/deploy.env)}"
: "${DEPLOY_APP_DIR:?Задайте DEPLOY_APP_DIR в deploy/deploy.env}"
SERVER="$DEPLOY_SERVER"
APP_DIR="$DEPLOY_APP_DIR"
JAR="target/predictions-1.0.0.jar"
REMOTE_SUDO="${DEPLOY_REMOTE_SUDO:-sudo}"
# user@host → user
DEPLOY_USER="${DEPLOY_USER:-${SERVER%%@*}}"

log() { echo "[upload-jar] $*"; }

log "Local Maven build..."
mvn -q -DskipTests package

[[ -f "$JAR" ]] || { log "ERROR: $JAR not found"; exit 1; }
size=$(stat -c%s "$JAR" 2>/dev/null || stat -f%z "$JAR")
[[ "$size" -gt 1000000 ]] || { log "ERROR: jar too small ($size bytes)"; exit 1; }

# Собрать unit из шаблона без секретов/абсолютных путей в git
TMP_UNIT=$(mktemp)
sed -e "s|REPLACE_USER|${DEPLOY_USER}|g" \
    -e "s|/path/to/predictions|${APP_DIR}|g" \
    deploy/predicts.service > "$TMP_UNIT"

log "Upload to $SERVER:$APP_DIR ..."
ssh "$SERVER" "$REMOTE_SUDO systemctl stop predicts 2>/dev/null || true; mkdir -p $APP_DIR/target $APP_DIR/deploy"
if ssh "$SERVER" "test -f $APP_DIR/target/predictions-1.0.0.jar"; then
    ssh "$SERVER" "cp -a $APP_DIR/target/predictions-1.0.0.jar $APP_DIR/target/predictions-1.0.0.jar.bak"
fi
scp "$JAR" "$SERVER:$APP_DIR/target/predictions-1.0.0.jar.new"
ssh "$SERVER" "mv -f $APP_DIR/target/predictions-1.0.0.jar.new $APP_DIR/target/predictions-1.0.0.jar && jar tf $APP_DIR/target/predictions-1.0.0.jar >/dev/null"
scp "$TMP_UNIT" "$SERVER:$APP_DIR/deploy/predicts.service"
rm -f "$TMP_UNIT"

log "Install unit + restart..."
ssh "$SERVER" "$REMOTE_SUDO bash -s" <<EOF
set -euo pipefail
install -m 644 $APP_DIR/deploy/predicts.service /etc/systemd/system/predicts.service
systemctl daemon-reload
systemctl reset-failed predicts 2>/dev/null || true
systemctl start predicts
sleep 6
systemctl is-active predicts
EOF

log "Done."
