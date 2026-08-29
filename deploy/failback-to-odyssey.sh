#!/bin/bash
# Возврат прода с VPS на Odyssey (после того как дом снова жив).
# Запускать НА VPS: sudo /home/predictions/deploy/failback-to-odyssey.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/predictions}"
ENDPOINT_FILE="${ENDPOINT_FILE:-$BACKUP_DIR/odyssey-endpoint.env}"
ODYSSEY_SSH_USER="${ODYSSEY_SSH_USER:-nikita}"
ODYSSEY_SSH_PORT="${ODYSSEY_SSH_PORT:-2222}"
ODYSSEY_HTTPS_PORT="${ODYSSEY_HTTPS_PORT:-443}"

log() { echo "$(date -Iseconds) [failback-to-odyssey] $*"; }

[[ "$(id -u)" -eq 0 ]] || { log "нужен root"; exit 1; }
[[ -f "$ENDPOINT_FILE" ]] && source "$ENDPOINT_FILE"
[[ -f "$APP_DIR/deploy/duckdns.env" ]] && source "$APP_DIR/deploy/duckdns.env"
[[ -f "$APP_DIR/deploy/orchestrator.env" ]] && source "$APP_DIR/deploy/orchestrator.env"

IP="${ODYSSEY_PUBLIC_IP:-${PUBLIC_IP:-}}"
DOMAIN="${DUCKDNS_FULL:-predicts.duckdns.org}"
[[ -n "$IP" ]] || { log "ERROR: нет ODYSSEY_PUBLIC_IP"; exit 1; }

log "Stop VPS predicts + caddy..."
systemctl stop predicts 2>/dev/null || true
systemctl stop caddy 2>/dev/null || true

log "DuckDNS → Odyssey IP $IP..."
if [[ -n "${DUCKDNS_DOMAIN:-}" && -n "${DUCKDNS_TOKEN:-}" ]]; then
  curl -fsS "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${IP}" || true
  echo
else
  log "WARN: нет duckdns token — DNS вручную"
fi

log "Start Odyssey predicts + caddy via SSH..."
ssh -o BatchMode=yes -o ConnectTimeout=12 -p "$ODYSSEY_SSH_PORT" "${ODYSSEY_SSH_USER}@${IP}" \
  'sudo systemctl reset-failed predicts 2>/dev/null || true
   sudo systemctl start caddy
   sudo systemctl start predicts
   for i in $(seq 1 30); do
     if systemctl is-active --quiet predicts && curl -sf -o /dev/null http://127.0.0.1:8080/miniapp/; then
       echo healthy
       exit 0
     fi
     sleep 3
   done
   systemctl --no-pager -l status predicts || true
   exit 1'

# обновить env на Odyssey под :443
WEBAPP_URL="https://${DOMAIN}/miniapp/"
if [[ "${ODYSSEY_HTTPS_PORT}" != "443" ]]; then
  WEBAPP_URL="https://${DOMAIN}:${ODYSSEY_HTTPS_PORT}/miniapp/"
fi
ssh -o BatchMode=yes -o ConnectTimeout=12 -p "$ODYSSEY_SSH_PORT" "${ODYSSEY_SSH_USER}@${IP}" \
  "grep -q '^HTTPS_PORT=' /home/nikita/predictions/deploy/predicts.env && \
   sed -i 's|^HTTPS_PORT=.*|HTTPS_PORT=${ODYSSEY_HTTPS_PORT}|' /home/nikita/predictions/deploy/predicts.env || true
   grep -q '^BOT_WEBAPP_URL=' /home/nikita/predictions/deploy/predicts.env && \
   sed -i \"s|^BOT_WEBAPP_URL=.*|BOT_WEBAPP_URL=${WEBAPP_URL}|\" /home/nikita/predictions/deploy/predicts.env || true
   grep -q '^PUBLIC_HTTPS_URL=' /home/nikita/predictions/deploy/predicts.env && \
   sed -i \"s|^PUBLIC_HTTPS_URL=.*|PUBLIC_HTTPS_URL=${WEBAPP_URL%/miniapp/}|\" /home/nikita/predictions/deploy/predicts.env || true
   sudo systemctl restart predicts 2>/dev/null || true" || true

mkdir -p /var/lib/predictions-orchestrator
echo odyssey > /var/lib/predictions-orchestrator/state
echo 0 > /var/lib/predictions-orchestrator/odyssey_fail_count
echo 0 > /var/lib/predictions-orchestrator/odyssey_ok_count

log "DONE. Mini App: ${WEBAPP_URL}"
