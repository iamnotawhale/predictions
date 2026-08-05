#!/bin/bash
# Локальный HTTPS-туннель для Telegram Mini App (cloudflared).
# Использование: ./scripts/dev-https.sh [порт приложения, по умолчанию 8080]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${1:-8080}"
SKIP_LOCAL_CHECK="${2:-}"
ENV_OUT="$ROOT/deploy/local-https.env"
PROPS_OUT="$ROOT/deploy/local-https.properties"
LOG_FILE="${TMPDIR:-/tmp}/predictions-cloudflared.log"
PID_FILE="${TMPDIR:-/tmp}/predictions-cloudflared.pid"

# shellcheck source=scripts/https-env.sh
source "$ROOT/scripts/https-env.sh"

if ! command -v cloudflared >/dev/null 2>&1 && [[ -x "$HOME/.local/bin/cloudflared" ]]; then
    export PATH="$HOME/.local/bin:$PATH"
fi

if ! command -v cloudflared >/dev/null 2>&1; then
    echo "cloudflared не установлен."
    echo "Установка: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/"
    echo "  или: curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared && chmod +x cloudflared"
    exit 1
fi

if [[ "$SKIP_LOCAL_CHECK" != "--no-local-check" ]] && ! curl -sf "http://127.0.0.1:$PORT/miniapp/" -o /dev/null 2>/dev/null; then
    echo "Приложение не отвечает на http://127.0.0.1:$PORT"
    echo "Сначала запустите бота, например:"
    echo "  java -jar target/predictions-1.0.0.jar --spring.config.location=file:./application.yml,file:./application-local.yml --spring.profiles.active=local"
    exit 1
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Останавливаю предыдущий туннель (pid $(cat "$PID_FILE"))..."
    kill "$(cat "$PID_FILE")" 2>/dev/null || true
    sleep 1
fi

echo "Запуск cloudflared → http://127.0.0.1:$PORT ..."
: > "$LOG_FILE"
cloudflared tunnel --url "http://127.0.0.1:$PORT" >>"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

PUBLIC_URL=""
for _ in $(seq 1 30); do
    PUBLIC_URL=$(grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' "$LOG_FILE" | head -1 || true)
    if [[ -n "$PUBLIC_URL" ]]; then
        break
    fi
    sleep 1
done

if [[ -z "$PUBLIC_URL" ]]; then
    echo "Не удалось получить URL туннеля. Лог: $LOG_FILE"
    kill "$(cat "$PID_FILE")" 2>/dev/null || true
    exit 1
fi

echo ""
echo "=== Локальный HTTPS ==="
https_write_env_file "$ENV_OUT" "$PUBLIC_URL"
WEBAPP_URL="$(https_derive_webapp_url "$PUBLIC_URL")"
cat > "$PROPS_OUT" <<EOF
bot.webAppUrl=$WEBAPP_URL
public.https.url=$PUBLIC_URL
EOF
chmod 600 "$PROPS_OUT" 2>/dev/null || true
echo ""
echo "Перезапустите приложение с этим env-файлом:"
echo "  export \$(grep -v '^#' $ENV_OUT | xargs)"
echo "  java -jar target/predictions-1.0.0.jar --spring.config.location=file:./application.yml,file:./$ENV_OUT"
echo ""
echo "Mini App в Telegram: $WEBAPP_URL"
echo "Spring properties: $PROPS_OUT"
echo "Туннель pid: $(cat "$PID_FILE") (остановка: kill \$(cat $PID_FILE))"
echo "Лог туннеля: $LOG_FILE"
