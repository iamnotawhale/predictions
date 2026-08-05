#!/bin/bash
# Локальный запуск с HTTPS для Telegram Mini App.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Поддержка локальной установки cloudflared без sudo (например, ~/.local/bin/cloudflared)
if ! command -v cloudflared >/dev/null 2>&1 && [[ -x "$HOME/.local/bin/cloudflared" ]]; then
    export PATH="$HOME/.local/bin:$PATH"
fi

if [[ ! -f target/predictions-1.0.0.jar ]]; then
    echo "Сборка..."
    mvn -q package -DskipTests
fi

load_env_file() {
    local file="$1"
    if [[ -f "$file" ]]; then
        # .env overrides application-local.yml values via environment variables
        set -a
        # shellcheck disable=SC1090
        source "$file"
        set +a
    fi
}

load_local_envs() {
    load_env_file "deploy/local.env"
    load_env_file "deploy/local-https.env"
}

run_app_background() {
    java -Duser.timezone=Europe/Moscow -jar target/predictions-1.0.0.jar \
        --spring.config.location=file:./application.yml,file:./application-local.yml \
        --spring.profiles.active=local &
}

echo "1) Запуск приложения (профиль local)..."
load_local_envs
run_app_background
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
bash "$ROOT/scripts/dev-https.sh" 8080

echo ""
echo "3) Перезапуск с BOT_WEBAPP_URL из deploy/local-https.env..."
kill "$APP_PID" 2>/dev/null || true
sleep 2

load_local_envs

echo ""
echo "Приложение: http://127.0.0.1:8080/miniapp/"
echo "Telegram Mini App: ${BOT_WEBAPP_URL:-не задан}"
echo "Браузер (dev): http://127.0.0.1:8080/miniapp/"
echo ""
exec java -Duser.timezone=Europe/Moscow -jar target/predictions-1.0.0.jar \
    --spring.config.location=file:./application.yml,file:./application-local.yml \
    --spring.profiles.active=local
