#!/usr/bin/env bash
# SSH-туннель к Postgres на проде (для IDEA Debug без открытия :5432 наружу).
# Использование: ./scripts/ssh-tunnel-prod-db.sh
# Затем JDBC test: jdbc:postgresql://localhost:15432/predicts_test
# Прод: jdbc:postgresql://localhost:15432/predicts_prod
set -euo pipefail

# Задайте DEPLOY_SERVER в окружении или deploy/deploy.env
: "${DEPLOY_SERVER:?Set DEPLOY_SERVER=user@host}"
LOCAL_PORT="${LOCAL_PORT:-15432}"
REMOTE_PORT="${REMOTE_PORT:-5432}"

echo "Tunnel: localhost:${LOCAL_PORT} -> ${DEPLOY_SERVER}:127.0.0.1:${REMOTE_PORT}"
echo "Stop with Ctrl+C"
exec ssh -N -L "${LOCAL_PORT}:127.0.0.1:${REMOTE_PORT}" "${DEPLOY_SERVER}"
