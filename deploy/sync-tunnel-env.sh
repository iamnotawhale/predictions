#!/bin/bash
# Читает URL из cloudflared (systemd journal) и обновляет predicts.env + перезапуск бота.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
ENV_FILE="${ENV_FILE:-$APP_DIR/deploy/predicts.env}"
JOURNAL_UNIT="${JOURNAL_UNIT:-predictions-tunnel}"
RESTART_SERVICE="${RESTART_SERVICE:-predicts}"

# shellcheck source=../scripts/https-env.sh
source "$APP_DIR/scripts/https-env.sh"

log() { echo "[sync-tunnel] $*"; }

URL=""
for _ in $(seq 1 45); do
    URL=$(journalctl -u "$JOURNAL_UNIT" --no-pager 2>/dev/null \
        | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | tail -1 || true)
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

if [[ "$CURRENT" == "$URL" ]]; then
    log "URL unchanged: $URL"
    exit 0
fi

log "New tunnel URL: $URL"
https_write_env_file "$ENV_FILE" "$URL"

if systemctl is-active --quiet "$RESTART_SERVICE" 2>/dev/null; then
    log "Restarting $RESTART_SERVICE..."
    systemctl restart "$RESTART_SERVICE"
fi

log "Done. Mini App: $(https_derive_webapp_url "$URL")"
