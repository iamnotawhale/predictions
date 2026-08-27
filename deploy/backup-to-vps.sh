#!/bin/bash
# Odyssey → VPS: дамп БД + актуальный jar/env для cold-standby.
# Не запускает бота на VPS (иначе будет два getUpdates).
set -euo pipefail

APP_DIR="${APP_DIR:-/home/nikita/predictions}"
cd "$APP_DIR"

if [[ -f deploy/backup.env ]]; then
  # shellcheck disable=SC1091
  source deploy/backup.env
fi
if [[ -f deploy/predicts.env ]]; then
  # shellcheck disable=SC1091
  source deploy/predicts.env
fi

: "${VPS_SSH:?Задайте VPS_SSH в deploy/backup.env}"
VPS_APP_DIR="${VPS_APP_DIR:-/home/predictions}"
VPS_BACKUP_DIR="${VPS_BACKUP_DIR:-/var/backups/predictions}"
KEEP_DUMPS="${KEEP_DUMPS:-8}"
JAR="$APP_DIR/target/predictions-1.0.0.jar"
DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/predicts_prod}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-admin}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD required}"

# jdbc:postgresql://host:port/db → host port db
DB_HOST=$(printf '%s' "$DB_URL" | sed -n 's|.*://\([^:/]*\).*|\1|p')
DB_PORT=$(printf '%s' "$DB_URL" | sed -n 's|.*://[^:]*:\([0-9]*\)/.*|\1|p')
DB_NAME=$(printf '%s' "$DB_URL" | sed -n 's|.*/\([^?]*\).*|\1|p')
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-predicts_prod}"

STAMP=$(date +%Y%m%d-%H%M%S)
DUMP_LOCAL="/tmp/predicts_prod-${STAMP}.dump"
log() { echo "[backup-to-vps] $*"; }

[[ -s "$JAR" ]] || { log "ERROR: missing $JAR"; exit 1; }

log "pg_dump ${DB_NAME}..."
export PGPASSWORD="$DB_PASS"
pg_dump -Fc -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$DUMP_LOCAL"
unset PGPASSWORD
DUMP_SIZE=$(stat -c%s "$DUMP_LOCAL")
[[ "$DUMP_SIZE" -gt 1000 ]] || { log "ERROR: dump too small"; exit 1; }

log "Ensure remote dirs on $VPS_SSH..."
ssh "$VPS_SSH" "mkdir -p '$VPS_APP_DIR/target' '$VPS_APP_DIR/deploy' '$VPS_BACKUP_DIR'"

log "Upload dump ($DUMP_SIZE bytes)..."
scp -q "$DUMP_LOCAL" "$VPS_SSH:$VPS_BACKUP_DIR/predicts_prod-${STAMP}.dump"
scp -q "$DUMP_LOCAL" "$VPS_SSH:$VPS_BACKUP_DIR/predicts_prod-latest.dump"
rm -f "$DUMP_LOCAL"

log "Sync jar + env (standby, bot stays stopped)..."
scp -q "$JAR" "$VPS_SSH:$VPS_APP_DIR/target/predictions-1.0.0.jar"
scp -q "$APP_DIR/deploy/predicts.env" "$VPS_SSH:$VPS_APP_DIR/deploy/predicts.env.odyssey"
[[ -f "$APP_DIR/deploy/duckdns.env" ]] && scp -q "$APP_DIR/deploy/duckdns.env" "$VPS_SSH:$VPS_APP_DIR/deploy/duckdns.env"
[[ -f "$APP_DIR/application.yml" ]] && scp -q "$APP_DIR/application.yml" "$VPS_SSH:$VPS_APP_DIR/application.yml"
[[ -f "$APP_DIR/application-prod.yml" ]] && scp -q "$APP_DIR/application-prod.yml" "$VPS_SSH:$VPS_APP_DIR/application-prod.yml"
# failover script from repo path if present next to this script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
[[ -f "$SCRIPT_DIR/failover-to-vps.sh" ]] && scp -q "$SCRIPT_DIR/failover-to-vps.sh" "$VPS_SSH:$VPS_APP_DIR/deploy/failover-to-vps.sh"
ssh "$VPS_SSH" "chmod 700 '$VPS_APP_DIR/deploy/predicts.env.odyssey' '$VPS_APP_DIR/deploy/duckdns.env' 2>/dev/null || true; chmod 755 '$VPS_APP_DIR/deploy/failover-to-vps.sh' 2>/dev/null || true"

log "Rotate dumps (keep $KEEP_DUMPS)..."
ssh "$VPS_SSH" "cd '$VPS_BACKUP_DIR' && ls -1t predicts_prod-*.dump 2>/dev/null | grep -v latest | tail -n +$((KEEP_DUMPS + 1)) | xargs -r rm -f"

log "Verify VPS predicts is NOT running..."
ssh "$VPS_SSH" "systemctl is-active predicts 2>/dev/null || true" | grep -qx active && {
  log "WARN: predicts active on VPS — stop it to avoid dual bot"
  ssh "$VPS_SSH" "systemctl stop predicts || true"
} || true

log "OK stamp=$STAMP → $VPS_SSH:$VPS_BACKUP_DIR"
