#!/bin/bash
# Отключить авто-failover: прод только на Odyssey. Запускать НА VPS (root).
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
STATE_DIR="${STATE_DIR:-/var/lib/predictions-orchestrator}"

log() { echo "$(date -Iseconds) [disable-vps-failover] $*"; }

[[ "$(id -u)" -eq 0 ]] || { log "нужен root"; exit 1; }

log "Stop and disable orchestrator timer..."
systemctl disable --now predictions-orchestrator.timer 2>/dev/null || true
systemctl reset-failed predictions-orchestrator.service 2>/dev/null || true

log "Stop VPS predicts + caddy (standby only, no prod on VPS)..."
systemctl stop predicts 2>/dev/null || true
systemctl disable predicts 2>/dev/null || true
systemctl stop caddy 2>/dev/null || true

ORCH_ENV="$APP_DIR/deploy/orchestrator.env"
if [[ -f "$ORCH_ENV" ]]; then
  for key in AUTO_FAILOVER AUTO_FAILBACK; do
    if grep -q "^${key}=" "$ORCH_ENV"; then
      sed -i "s/^${key}=.*/${key}=false/" "$ORCH_ENV"
    else
      echo "${key}=false" >> "$ORCH_ENV"
    fi
  done
  log "Updated $ORCH_ENV: AUTO_FAILOVER=false, AUTO_FAILBACK=false"
fi

mkdir -p "$STATE_DIR"
echo odyssey > "$STATE_DIR/state"
echo 0 > "$STATE_DIR/odyssey_fail_count"
echo 0 > "$STATE_DIR/odyssey_ok_count"
log "Orchestrator state=odyssey (fail/ok counts reset)"

log "DONE. Прод только на Odyssey; оркестратор на VPS выключен."
log "Проверь дома: systemctl is-active predicts caddy && curl -sf http://127.0.0.1:8080/miniapp/"
