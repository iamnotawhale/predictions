#!/bin/bash
# Читает URL из cloudflared (systemd journal), обновляет predicts.env и кнопку Mini App в Telegram.
# Бот НЕ перезапускается — иначе polling падает на ~40 с при каждой смене URL туннеля.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
ENV_FILE="${ENV_FILE:-$APP_DIR/deploy/predicts.env}"
JOURNAL_UNIT="${JOURNAL_UNIT:-predictions-tunnel}"
# Перезапуск только если явно задано: RESTART_SERVICE=predicts ./sync-tunnel-env.sh
RESTART_SERVICE="${RESTART_SERVICE:-}"

# shellcheck source=../scripts/https-env.sh
source "$APP_DIR/scripts/https-env.sh"

log() { echo "[sync-tunnel] $*"; }

read_bot_token() {
    if [[ -f "$ENV_FILE" ]]; then
        # shellcheck disable=SC1090
        set -a
        source "$ENV_FILE"
        set +a
        if [[ -n "${BOT_TOKEN:-}" ]]; then
            echo "$BOT_TOKEN"
            return 0
        fi
    fi
    return 1
}

set_menu_button() {
    local webapp_url="$1"
    local token
    token=$(read_bot_token) || { log "WARN: cannot read bot token, skip menu button"; return 0; }

    local payload
    payload=$(python3 -c "
import json, sys
print(json.dumps({
    'menu_button': {
        'type': 'web_app',
        'text': 'Открыть приложение',
        'web_app': {'url': sys.argv[1]}
    }
}))
" "$webapp_url")

    local resp
    resp=$(curl -sf -X POST "https://api.telegram.org/bot${token}/setChatMenuButton" \
        -H 'Content-Type: application/json' \
        -d "$payload" 2>&1) || {
        log "WARN: setChatMenuButton failed: $resp"
        return 0
    }
    log "Menu button updated via API"
}

# Берем URL только из текущего запуска unit (иначе подхватывается старый из journal)
URL=""
SINCE=$(systemctl show -p ActiveEnterTimestamp --value "$JOURNAL_UNIT" 2>/dev/null || true)
for _ in $(seq 1 60); do
    if [[ -n "$SINCE" && "$SINCE" != "n/a" ]]; then
        URL=$(journalctl -u "$JOURNAL_UNIT" --since "$SINCE" --no-pager 2>/dev/null \
            | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | tail -1 || true)
    else
        URL=$(journalctl -u "$JOURNAL_UNIT" -n 80 --no-pager 2>/dev/null \
            | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | tail -1 || true)
    fi
    if [[ -n "$URL" ]]; then
        break
    fi
    sleep 1
done

if [[ -z "$URL" ]]; then
    log "ERROR: trycloudflare URL not found in journal ($JOURNAL_UNIT)"
    exit 1
fi

CURRENT=""
if [[ -f "$ENV_FILE" ]]; then
    CURRENT=$(grep '^PUBLIC_HTTPS_URL=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)
fi

WEBAPP_URL="$(https_derive_webapp_url "$URL")"

if [[ "$CURRENT" == "$URL" ]]; then
    if curl -sf --max-time 8 "${URL}/miniapp/" -o /dev/null 2>/dev/null; then
        log "URL unchanged and reachable: $URL"
        exit 0
    fi
    log "WARN: URL in env matches journal but tunnel is down — updating menu anyway"
fi

log "New tunnel URL: $URL"
https_write_env_file "$ENV_FILE" "$URL"

set_menu_button "$WEBAPP_URL"

if [[ -n "$RESTART_SERVICE" ]] && systemctl is-active --quiet "$RESTART_SERVICE" 2>/dev/null; then
    log "Restarting $RESTART_SERVICE (RESTART_SERVICE was set)..."
    systemctl restart "$RESTART_SERVICE"
fi

log "Done. Mini App: $WEBAPP_URL (bot not restarted)"
