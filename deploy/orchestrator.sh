#!/bin/bash
# Health-оркестратор на VPS: Odyssey primary ↔ VPS failover.
# Проверки: SSH+HTTP на Odyssey, локальный miniapp на VPS.
# Алерты → Telegram ADMIN_CHAT_ID. Не запускать на Odyssey.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/predictions}"
STATE_DIR="${STATE_DIR:-/var/lib/predictions-orchestrator}"
LOG_FILE="${LOG_FILE:-$APP_DIR/logs/orchestrator.log}"
ENDPOINT_FILE="${ENDPOINT_FILE:-$BACKUP_DIR/odyssey-endpoint.env}"

mkdir -p "$STATE_DIR" "$(dirname "$LOG_FILE")" "$APP_DIR/logs"
if [[ -f "$LOG_FILE" ]] && [[ "$(stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)" -gt 1048576 ]]; then
  mv -f "$LOG_FILE" "${LOG_FILE}.1"
fi
exec > >(tee -a "$LOG_FILE") 2>&1

[[ -f "$APP_DIR/deploy/orchestrator.env" ]] && source "$APP_DIR/deploy/orchestrator.env"
[[ -f "$ENDPOINT_FILE" ]] && source "$ENDPOINT_FILE"
[[ -f "$APP_DIR/deploy/predicts.env.odyssey" ]] && source "$APP_DIR/deploy/predicts.env.odyssey"
[[ -f "$APP_DIR/deploy/duckdns.env" ]] && source "$APP_DIR/deploy/duckdns.env"

FAILURES_BEFORE_FAILOVER="${FAILURES_BEFORE_FAILOVER:-3}"
SUCCESSES_BEFORE_FAILBACK="${SUCCESSES_BEFORE_FAILBACK:-3}"
AUTO_FAILOVER="${AUTO_FAILOVER:-true}"
AUTO_FAILBACK="${AUTO_FAILBACK:-true}"
ODYSSEY_SSH_USER="${ODYSSEY_SSH_USER:-nikita}"
ODYSSEY_SSH_PORT="${ODYSSEY_SSH_PORT:-2222}"
ODYSSEY_HTTPS_PORT="${ODYSSEY_HTTPS_PORT:-8443}"
ODYSSEY_PUBLIC_IP="${ODYSSEY_PUBLIC_IP:-${PUBLIC_IP:-}}"
DOMAIN="${DUCKDNS_FULL:-predicts.duckdns.org}"

STATE_FILE="$STATE_DIR/state"
FAIL_COUNT_FILE="$STATE_DIR/odyssey_fail_count"
OK_COUNT_FILE="$STATE_DIR/odyssey_ok_count"

log() { echo "$(date -Iseconds) [orchestrator] $*"; }

tg() {
  local text="$1"
  [[ -n "${BOT_TOKEN:-}" && -n "${ADMIN_CHAT_ID:-}" ]] || { log "WARN: no BOT_TOKEN/ADMIN_CHAT_ID — skip TG"; return 0; }
  curl -fsS -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    --data-urlencode "chat_id=${ADMIN_CHAT_ID}" \
    --data-urlencode "text=${text}" \
    --data-urlencode "disable_web_page_preview=true" >/dev/null \
    || log "WARN: telegram send failed"
}

read_state() {
  if [[ -f "$STATE_FILE" ]]; then cat "$STATE_FILE"; else echo odyssey; fi
}
write_state() { printf '%s\n' "$1" > "$STATE_FILE"; }
read_count() { local f="$1"; [[ -f "$f" ]] && cat "$f" || echo 0; }
write_count() { printf '%s\n' "$2" > "$1"; }

probe_odyssey() {
  local ip="$ODYSSEY_PUBLIC_IP"
  [[ -n "$ip" ]] || { log "Odyssey probe: no ODYSSEY_PUBLIC_IP"; return 1; }

  # 1) SSH + local miniapp (самый надёжный признак живого приложения)
  if ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=8 \
      -p "$ODYSSEY_SSH_PORT" "${ODYSSEY_SSH_USER}@${ip}" \
      'systemctl is-active --quiet predicts && curl -sf -o /dev/null --max-time 5 http://127.0.0.1:8080/miniapp/' 2>/dev/null; then
    log "Odyssey OK via SSH ${ip}:${ODYSSEY_SSH_PORT}"
    return 0
  fi

  # 2) внешний HTTPS на домашнем IP:8443 (без зависимости от DuckDNS)
  if curl -skf --connect-timeout 8 --max-time 12 \
      --resolve "${DOMAIN}:${ODYSSEY_HTTPS_PORT}:${ip}" \
      "https://${DOMAIN}:${ODYSSEY_HTTPS_PORT}/miniapp/" -o /dev/null; then
    log "Odyssey OK via HTTPS ${ip}:${ODYSSEY_HTTPS_PORT}"
    return 0
  fi

  log "Odyssey FAIL ip=$ip"
  return 1
}

probe_vps_local() {
  if systemctl is-active --quiet predicts && curl -sf -o /dev/null --max-time 5 http://127.0.0.1:8080/miniapp/; then
    log "VPS local predicts OK"
    return 0
  fi
  log "VPS local predicts FAIL"
  return 1
}

do_failover() {
  log "ACTION failover → VPS"
  tg "⚠️ Odyssey недоступен (${FAILURES_BEFORE_FAILOVER} проверок). Запускаю failover на VPS…"
  if [[ ! -x "$APP_DIR/deploy/failover-to-vps.sh" ]]; then
    log "ERROR: missing failover-to-vps.sh"
    tg "❌ failover-to-vps.sh не найден на VPS"
    return 1
  fi
  if "$APP_DIR/deploy/failover-to-vps.sh"; then
    write_state vps
    write_count "$FAIL_COUNT_FILE" 0
    write_count "$OK_COUNT_FILE" 0
    tg "✅ Failover на VPS успешен. Mini App: https://${DOMAIN}/miniapp/"
    return 0
  fi
  tg "❌ Failover на VPS ПРОВАЛИЛСЯ — смотри логи на VPS"
  return 1
}

do_failback() {
  log "ACTION failback → Odyssey"
  tg "ℹ️ Odyssey снова жив (${SUCCESSES_BEFORE_FAILBACK} проверок). Возвращаю прод на Odyssey…"
  if [[ ! -x "$APP_DIR/deploy/failback-to-odyssey.sh" ]]; then
    log "ERROR: missing failback-to-odyssey.sh"
    tg "❌ failback-to-odyssey.sh не найден"
    return 1
  fi
  if "$APP_DIR/deploy/failback-to-odyssey.sh"; then
    write_state odyssey
    write_count "$FAIL_COUNT_FILE" 0
    write_count "$OK_COUNT_FILE" 0
    tg "✅ Failback на Odyssey успешен. Mini App: https://${DOMAIN}:${ODYSSEY_HTTPS_PORT}/miniapp/"
    return 0
  fi
  tg "❌ Failback на Odyssey ПРОВАЛИЛСЯ — прод мог остаться на VPS"
  return 1
}

log "START state=$(read_state) ip=${ODYSSEY_PUBLIC_IP:-none}"

primary="$(read_state)"
fails="$(read_count "$FAIL_COUNT_FILE")"
oks="$(read_count "$OK_COUNT_FILE")"

if probe_odyssey; then
  odyssey_ok=1
  fails=0
  oks=$((oks + 1))
else
  odyssey_ok=0
  oks=0
  fails=$((fails + 1))
fi
write_count "$FAIL_COUNT_FILE" "$fails"
write_count "$OK_COUNT_FILE" "$oks"
log "counts fail=$fails ok=$oks primary=$primary"

if [[ "$primary" == "odyssey" ]]; then
  if [[ "$odyssey_ok" -eq 0 ]]; then
    if [[ "$fails" -eq 1 ]] || [[ "$fails" -eq "$FAILURES_BEFORE_FAILOVER" ]]; then
      tg "⚠️ Odyssey check FAIL ($fails/$FAILURES_BEFORE_FAILOVER). IP=${ODYSSEY_PUBLIC_IP:-?}"
    fi
    if [[ "$fails" -ge "$FAILURES_BEFORE_FAILOVER" ]]; then
      if [[ "$AUTO_FAILOVER" == "true" ]]; then
        do_failover || true
      else
        tg "AUTO_FAILOVER=false — переключи вручную: sudo $APP_DIR/deploy/failover-to-vps.sh"
      fi
    fi
  else
    # primary odyssey healthy — убедиться что VPS бот не работает параллельно
    if systemctl is-active --quiet predicts 2>/dev/null; then
      log "WARN: predicts active on VPS while primary=odyssey — stopping"
      systemctl stop predicts 2>/dev/null || true
      tg "⚠️ На VPS был запущен predicts при primary=Odyssey — остановил, чтобы не было двух ботов."
    fi
  fi
elif [[ "$primary" == "vps" ]]; then
  if ! probe_vps_local; then
    tg "🚨 Primary=VPS, но локальный predicts мёртв! Проверь VPS вручную."
  fi
  if [[ "$odyssey_ok" -eq 1 ]]; then
    if [[ "$oks" -eq 1 ]] || [[ "$oks" -eq "$SUCCESSES_BEFORE_FAILBACK" ]]; then
      tg "ℹ️ Odyssey снова отвечает ($oks/$SUCCESSES_BEFORE_FAILBACK)."
    fi
    if [[ "$oks" -ge "$SUCCESSES_BEFORE_FAILBACK" ]]; then
      if [[ "$AUTO_FAILBACK" == "true" ]]; then
        do_failback || true
      else
        tg "AUTO_FAILBACK=false — верни вручную: sudo $APP_DIR/deploy/failback-to-odyssey.sh"
      fi
    fi
  fi
else
  log "WARN: unknown state '$primary' — reset to odyssey"
  write_state odyssey
fi

log "END state=$(read_state)"
