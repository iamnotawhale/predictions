#!/bin/bash
# Деплой на сервер. На машине с 1 ГБ RAM лучше: BUILD_LOCALLY=1 ./deploy_predicts.sh
set -euo pipefail

APP_DIR="/home/predictions"
SERVICE="predicts"
ENV_FILE="$APP_DIR/deploy/predicts.env"
JAR="$APP_DIR/target/predictions-1.0.0.jar"
JAR_BAK="$APP_DIR/target/predictions-1.0.0.jar.bak"
TODAY=$(date +"%Y-%m-%d")
BUILD_LOCALLY="${BUILD_LOCALLY:-0}"

DOMAIN_ARG="${1:-}"
WEBAPP_URL="${WEBAPP_URL:-}"
PUBLIC_HTTPS_URL="${PUBLIC_HTTPS_URL:-}"
PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../scripts/https-env.sh
source "$SCRIPT_DIR/../scripts/https-env.sh"

log() { echo "[deploy] $*"; }

validate_jar() {
    local f="$1"
    [[ -f "$f" ]] || return 1
    local size
    size=$(stat -c%s "$f" 2>/dev/null || stat -f%z "$f")
    [[ "$size" -gt 1000000 ]] || return 1
    jar tf "$f" >/dev/null 2>&1
}

echo "=========================================="
echo "Deploy started at $(date)"
echo "=========================================="

cd "$APP_DIR"

log "[1/7] Git checkout master..."
git checkout master

log "[2/7] Git pull..."
git pull
echo "Git HEAD: $(git log --oneline -1)"

log "[3/7] Configure env..."
mkdir -p "$(dirname "$ENV_FILE")" "$APP_DIR/target"
touch "$ENV_FILE"

if [[ -n "$DOMAIN_ARG" ]]; then
    PUBLIC_HTTPS_URL="$(https_from_domain "$DOMAIN_ARG")"
    PUBLIC_DOMAIN="${DOMAIN_ARG#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN#http://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
elif [[ -n "$PUBLIC_HTTPS_URL" ]]; then
    PUBLIC_HTTPS_URL="$(https_strip_trailing_slash "$PUBLIC_HTTPS_URL")"
    PUBLIC_DOMAIN="${PUBLIC_HTTPS_URL#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
elif [[ -n "$WEBAPP_URL" ]]; then
    PUBLIC_HTTPS_URL="$(https_strip_trailing_slash "${WEBAPP_URL%/miniapp}")"
    PUBLIC_HTTPS_URL="${PUBLIC_HTTPS_URL%/miniapp/}"
    PUBLIC_DOMAIN="${PUBLIC_HTTPS_URL#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
fi

if [[ -n "${PUBLIC_HTTPS_URL:-}" ]]; then
    https_write_env_file "$ENV_FILE" "$PUBLIC_HTTPS_URL"
    if grep -q '^PUBLIC_DOMAIN=' "$ENV_FILE" 2>/dev/null; then
        sed -i "s|^PUBLIC_DOMAIN=.*|PUBLIC_DOMAIN=$PUBLIC_DOMAIN|" "$ENV_FILE"
    else
        echo "PUBLIC_DOMAIN=$PUBLIC_DOMAIN" >> "$ENV_FILE"
    fi
    grep -q '^APP_PORT=' "$ENV_FILE" || echo "APP_PORT=8080" >> "$ENV_FILE"
else
    log "HTTPS: не задан (передайте домен: ./deploy_predicts.sh your.domain.com)"
    grep -q '^BOT_WEBAPP_URL=' "$ENV_FILE" || echo "BOT_WEBAPP_URL=" >> "$ENV_FILE"
fi
grep -q '^SPRING_PROFILES_ACTIVE=' "$ENV_FILE" 2>/dev/null || echo "SPRING_PROFILES_ACTIVE=prod" >> "$ENV_FILE"
grep -q '^BOT_USERNAME=' "$ENV_FILE" 2>/dev/null || echo "BOT_USERNAME=" >> "$ENV_FILE"
grep -q '^BOT_CHAT_ID=' "$ENV_FILE" 2>/dev/null || echo "BOT_CHAT_ID=" >> "$ENV_FILE"
grep -q '^SPRING_DATASOURCE_PASSWORD=' "$ENV_FILE" 2>/dev/null || echo "SPRING_DATASOURCE_PASSWORD=" >> "$ENV_FILE"
if ! grep -q '^BOT_TOKEN=.\+' "$ENV_FILE" 2>/dev/null; then
    log "WARN: BOT_TOKEN не задан в $ENV_FILE"
fi
chmod 600 "$ENV_FILE"

log "[4/7] Stop service before build..."
systemctl stop "$SERVICE" 2>/dev/null || true
pkill -f 'mvn.*predictions' 2>/dev/null || true
sleep 2

if [[ "$BUILD_LOCALLY" == "1" ]]; then
    log "BUILD_LOCALLY=1 — ожидаем готовый jar (загрузите deploy/upload-jar.sh с локальной машины)"
    if ! validate_jar "$JAR"; then
        log "ERROR: $JAR отсутствует или битый. Запустите с локальной машины: ./deploy/upload-jar.sh"
        exit 1
    fi
else
    log "[4/7] Maven build on server (MAVEN_OPTS=-Xmx256m)..."
    if validate_jar "$JAR"; then
        cp -a "$JAR" "$JAR_BAK"
        log "Backup: $JAR_BAK"
    fi
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    export PATH="$JAVA_HOME/bin:$PATH"
    export MAVEN_OPTS="-Xmx256m -XX:+ExitOnOutOfMemoryError"
    rm -f "$JAR"
    if ! mvn -B -DskipTests package; then
        log "ERROR: Maven build failed"
        if validate_jar "$JAR_BAK"; then
            cp -a "$JAR_BAK" "$JAR"
            log "Restored jar from backup"
        fi
        exit 1
    fi
    if ! validate_jar "$JAR"; then
        log "ERROR: jar invalid after build"
        if validate_jar "$JAR_BAK"; then
            cp -a "$JAR_BAK" "$JAR"
            log "Restored jar from backup"
        fi
        exit 1
    fi
fi
log "Jar OK: $(du -h "$JAR" | cut -f1)"

log "[5/7] Install systemd units..."
install -m 644 "$APP_DIR/deploy/predicts.service" /etc/systemd/system/predicts.service
systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl reset-failed "$SERVICE" 2>/dev/null || true

log "[6/7] HTTPS (Caddy)..."
if [[ -n "${PUBLIC_DOMAIN:-}" ]]; then
    bash "$APP_DIR/deploy/setup-https.sh"
else
    log "Skip Caddy (PUBLIC_DOMAIN not configured)"
fi

log "[7/7] Start application..."
systemctl start "$SERVICE"
sleep 5
if ! systemctl is-active --quiet "$SERVICE"; then
    journalctl -u "$SERVICE" -n 30 --no-pager
    exit 1
fi
systemctl --no-pager status "$SERVICE" | head -15

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE" 2>/dev/null || true
    [[ -n "${BOT_WEBAPP_URL:-}" ]] && log "Mini App: $BOT_WEBAPP_URL"
fi

echo ""
echo "=========================================="
echo "Deploy complete at $(date)"
echo "=========================================="
tail -n 20 "$APP_DIR/logs/server-${TODAY}.log" 2>/dev/null || journalctl -u "$SERVICE" -n 20 --no-pager
