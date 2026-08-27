#!/bin/bash
# Аварийный запуск прода на VPS, когда Odyssey недоступен.
# Запускать НА VPS: sudo /home/predictions/deploy/failover-to-vps.sh
#
# Делает: restore latest dump → predicts.env под :443 → DuckDNS на IP VPS → caddy+predicts.
# Важно: бот может быть только в одном месте. Скрипт пытается остановить Odyssey.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/predictions}"
DUMP="${DUMP:-$BACKUP_DIR/predicts_prod-latest.sql.gz}"
# backward-compat: старый custom dump больше не поддерживается (PG18→PG16)
if [[ ! -s "$DUMP" && -s "$BACKUP_DIR/predicts_prod-latest.dump" ]]; then
  log "ERROR: найден только старый .dump (custom). Нужен новый бэкап plain SQL (.sql.gz) с Odyssey."
  exit 1
fi
ODYSSEY_SSH="${ODYSSEY_SSH:-nikita@192.168.1.38}"
# WAN fallback если LAN недоступен с VPS
ODYSSEY_SSH_WAN="${ODYSSEY_SSH_WAN:-nikita@predicts.duckdns.org}"
ODYSSEY_SSH_WAN_PORT="${ODYSSEY_SSH_WAN_PORT:-2222}"

log() { echo "[failover-to-vps] $*"; }

[[ "$(id -u)" -eq 0 ]] || { log "Запустите от root (sudo)"; exit 1; }
[[ -s "$DUMP" ]] || { log "ERROR: нет дампа $DUMP — сначала нужен backup-to-vps с Odyssey"; exit 1; }
[[ -s "$APP_DIR/target/predictions-1.0.0.jar" ]] || { log "ERROR: нет jar"; exit 1; }
[[ -f "$APP_DIR/deploy/predicts.env.odyssey" ]] || { log "ERROR: нет predicts.env.odyssey"; exit 1; }

log "Try stop Odyssey bot (best-effort)..."
ssh -o BatchMode=yes -o ConnectTimeout=5 "$ODYSSEY_SSH" 'sudo systemctl stop predicts caddy 2>/dev/null || true' 2>/dev/null \
  || ssh -o BatchMode=yes -o ConnectTimeout=8 -p "$ODYSSEY_SSH_WAN_PORT" "$ODYSSEY_SSH_WAN" 'sudo systemctl stop predicts caddy 2>/dev/null || true' 2>/dev/null \
  || log "WARN: Odyssey unreachable — продолжаем (убедитесь, что домашний бот мёртв)"

log "Prepare predicts.env for VPS (:443)..."
# shellcheck disable=SC1091
source "$APP_DIR/deploy/predicts.env.odyssey"
DOMAIN="${PUBLIC_DOMAIN:-${DUCKDNS_FULL:-predicts.duckdns.org}}"
if [[ -f "$APP_DIR/deploy/duckdns.env" ]]; then
  # shellcheck disable=SC1091
  source "$APP_DIR/deploy/duckdns.env"
  DOMAIN="${DUCKDNS_FULL:-$DOMAIN}"
fi
WEBAPP="https://${DOMAIN}/miniapp/"
PUBLIC_URL="https://${DOMAIN}"

umask 077
cat > "$APP_DIR/deploy/predicts.env" <<EOF
SPRING_PROFILES_ACTIVE=prod
BOT_USERNAME=${BOT_USERNAME:-}
BOT_TOKEN=${BOT_TOKEN:-}
BOT_CHAT_ID=${BOT_CHAT_ID:-}
ADMIN_CHAT_ID=${ADMIN_CHAT_ID:-}
SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-}
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/predicts_prod}
API_FOOTBALL_TOKEN=${API_FOOTBALL_TOKEN:-}
APP_PORT=${APP_PORT:-8080}
PUBLIC_DOMAIN=${DOMAIN}
PUBLIC_HTTPS_URL=${PUBLIC_URL}
BOT_WEBAPP_URL=${WEBAPP}
HTTPS_PORT=443
EOF
chmod 600 "$APP_DIR/deploy/predicts.env"

log "Restore DB from $DUMP..."
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:?}"
sudo -u postgres psql -d postgres -v ON_ERROR_STOP=1 <<'SQL'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'predicts_prod' AND pid <> pg_backend_pid();
SQL
sudo -u postgres psql -d postgres -c "DROP DATABASE IF EXISTS predicts_prod;"
sudo -u postgres psql -d postgres -c "CREATE DATABASE predicts_prod OWNER admin;"
if [[ "$DUMP" == *.gz ]]; then
  gunzip -c "$DUMP" | grep -v -E '^\\(un)?restrict |^SET transaction_timeout' \
    | psql -h localhost -U admin -d predicts_prod -v ON_ERROR_STOP=1
else
  grep -v -E '^\\(un)?restrict |^SET transaction_timeout' "$DUMP" \
    | psql -h localhost -U admin -d predicts_prod -v ON_ERROR_STOP=1
fi
unset PGPASSWORD
TABLES=$(sudo -u postgres psql -d predicts_prod -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
log "tables in public schema: $TABLES"
[[ "$TABLES" -gt 0 ]] || { log "ERROR: restore produced empty DB"; exit 1; }
sudo -u postgres psql -d predicts_prod -c '\dt' | head -20

log "Install systemd unit if needed..."
if [[ ! -f /etc/systemd/system/predicts.service ]]; then
  cat > /etc/systemd/system/predicts.service <<'UNIT'
[Unit]
Description=Predictions Telegram bot
After=network-online.target postgresql.service
Wants=network-online.target
StartLimitIntervalSec=300
StartLimitBurst=5

[Service]
Type=simple
User=root
WorkingDirectory=/home/predictions
EnvironmentFile=-/home/predictions/deploy/predicts.env
Environment=TZ=Europe/Moscow
ExecStartPre=/bin/bash -c 'test -s /home/predictions/target/predictions-1.0.0.jar && jar tf /home/predictions/target/predictions-1.0.0.jar >/dev/null'
ExecStart=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -Duser.timezone=Europe/Moscow -Xms128m -Xmx384m -jar /home/predictions/target/predictions-1.0.0.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=15
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
UNIT
fi

log "Caddyfile for :443..."
mkdir -p /etc/caddy
cat > /etc/caddy/Caddyfile <<EOF
${DOMAIN} {
	reverse_proxy 127.0.0.1:8080
	encode gzip
}
EOF

log "DuckDNS → this VPS public IP..."
if [[ -f "$APP_DIR/deploy/duckdns.env" ]]; then
  # shellcheck disable=SC1091
  source "$APP_DIR/deploy/duckdns.env"
  VPS_IP=$(curl -4 -fsS --max-time 8 ifconfig.me || curl -4 -fsS --max-time 8 icanhazip.com || true)
  if [[ -n "${DUCKDNS_DOMAIN:-}" && -n "${DUCKDNS_TOKEN:-}" && -n "$VPS_IP" ]]; then
    curl -fsS "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${VPS_IP}" || true
    echo
    log "DuckDNS updated to $VPS_IP"
  else
    log "WARN: cannot update DuckDNS (missing token or IP)"
  fi
fi

log "Start caddy + predicts..."
systemctl daemon-reload
systemctl enable --now caddy 2>/dev/null || systemctl restart caddy
systemctl reset-failed predicts 2>/dev/null || true
systemctl restart predicts

ok=0
for i in $(seq 1 40); do
  if systemctl is-active --quiet predicts && curl -sf -o /dev/null http://127.0.0.1:8080/miniapp/; then
    log "healthy after ${i} checks"
    ok=1
    break
  fi
  sleep 3
done
[[ "$ok" -eq 1 ]] || { systemctl --no-pager -l status predicts || true; journalctl -u predicts -n 60 --no-pager || true; exit 1; }

log "DONE. Mini App: ${WEBAPP}"
log "Проверь бота в Telegram. Когда Odyssey оживёт — не запускай predicts там, пока не сделаешь failback."
