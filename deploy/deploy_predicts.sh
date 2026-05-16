#!/bin/bash
set -euo pipefail

APP_DIR="/home/predictions"
SERVICE="predicts"
TODAY=$(date +"%Y-%m-%d")

echo "=========================================="
echo "Deploy started at $(date)"
echo "=========================================="

cd "$APP_DIR"

echo "[1/5] Git checkout v2-lite..."
git checkout v2-lite

echo "[2/5] Git pull..."
git pull
echo "Git HEAD: $(git log --oneline -1)"

echo "[3/5] Maven build..."
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx384m"
mvn -B -DskipTests package
echo "Build finished at $(date)"

echo "[4/5] Install systemd unit..."
install -m 644 "$APP_DIR/deploy/predicts.service" /etc/systemd/system/predicts.service
systemctl daemon-reload
systemctl enable "$SERVICE"

echo "[5/5] Restart service..."
systemctl restart "$SERVICE"
sleep 3
systemctl --no-pager status "$SERVICE"

echo ""
echo "=========================================="
echo "Deploy complete at $(date)"
echo "=========================================="
echo "Last 30 log lines:"
tail -n 30 "$APP_DIR/logs/server-${TODAY}.log" 2>/dev/null || journalctl -u "$SERVICE" -n 30 --no-pager
