#!/bin/bash
# Локальный запуск с HTTPS для Telegram Mini App.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f target/predictions-1.0.0.jar ]]; then
    echo "Сборка..."
    mvn -q package -DskipTests
fi

config_locations() {
    local loc="file:./application.yml,file:./application-local.yml"
    [[ -f deploy/local-https.env ]] && loc="$loc,file:./deploy/local-https.env"
    [[ -f deploy/local.env ]] && loc="$loc,file:./deploy/local.env"
    echo "$loc"
}

echo "1) Запуск приложения (профиль local)..."
java -Duser.timezone=Europe/Moscow -jar target/predictions-1.0.0.jar \
    --spring.config.location="$(config_locations)" \
    --spring.profiles.active=local &
APP_PID=$!

cleanup() {
    kill "$APP_PID" 2>/dev/null || true
    if [[ -f "${TMPDIR:-/tmp}/predictions-cloudflared.pid" ]]; then
        kill "$(cat "${TMPDIR:-/tmp}/predictions-cloudflared.pid")" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 45); do
    curl -sf http://127.0.0.1:8080/miniapp/ -o /dev/null && break
    sleep 1
done

echo ""
echo "2) HTTPS-туннель (cloudflared)..."
"$ROOT/scripts/dev-https.sh" 8080

echo ""
echo "3) Перезапуск с BOT_WEBAPP_URL из deploy/local-https.env..."
kill "$APP_PID" 2>/dev/null || true
sleep 2

# shellcheck disable=SC1091
[[ -f deploy/local-https.env ]] && source deploy/local-https.env

echo ""
echo "Приложение: http://127.0.0.1:8080/miniapp/"
echo "Telegram Mini App: ${BOT_WEBAPP_URL:-не задан}"
echo "Браузер (dev): http://127.0.0.1:8080/miniapp/"
echo ""
exec java -Duser.timezone=Europe/Moscow -jar target/predictions-1.0.0.jar \
    --spring.config.location="$(config_locations)" \
    --spring.profiles.active=local
