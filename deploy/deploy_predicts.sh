#!/bin/bash
set -euo pipefail

APP_DIR="/home/predictions"
SERVICE="predicts"
ENV_FILE="$APP_DIR/deploy/predicts.env"
TODAY=$(date +"%Y-%m-%d")

# HTTPS: ./deploy_predicts.sh predicts.example.com
#    или: WEBAPP_URL=https://domain/miniapp/ ./deploy_predicts.sh
#    или: PUBLIC_HTTPS_URL=https://domain ./deploy_predicts.sh
DOMAIN_ARG="${1:-}"
WEBAPP_URL="${WEBAPP_URL:-}"
PUBLIC_HTTPS_URL="${PUBLIC_HTTPS_URL:-}"
PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../scripts/https-env.sh
source "$SCRIPT_DIR/../scripts/https-env.sh"

echo "=========================================="
echo "Deploy started at $(date)"
echo "=========================================="

cd "$APP_DIR"

echo "[1/7] Git checkout v2-lite..."
git checkout v2-lite

echo "[2/7] Git pull..."
git pull
echo "Git HEAD: $(git log --oneline -1)"

echo "[3/7] Configure HTTPS env..."
mkdir -p "$(dirname "$ENV_FILE")"
touch "$ENV_FILE"

if [[ -n "$DOMAIN_ARG" ]]; then
    PUBLIC_HTTPS_URL="$(https_from_domain "$DOMAIN_ARG")"
    PUBLIC_DOMAIN="${DOMAIN_ARG#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN#http://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
elif [[ -n "$PUBLIC_HTTPS_URL" ]]; then
    PUBLIC_HTTPS_URL="$(https_strip_trailing_slash "$PUBLIC_HTTPS_URL")"
    PUBLIC_DOMAIN="${PUBLIC_HTTPS_URL#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
elif [[ -n "$WEBAPP_URL" ]]; then
    PUBLIC_HTTPS_URL="$(https_strip_trailing_slash "${WEBAPP_URL%/miniapp}")"
    PUBLIC_HTTPS_URL="${PUBLIC_HTTPS_URL%/miniapp/}"
    PUBLIC_DOMAIN="${PUBLIC_HTTPS_URL#https://}"
    PUBLIC_DOMAIN="${PUBLIC_DOMAIN%%/*}"
fi

if [[ -n "${PUBLIC_HTTPS_URL:-}" ]]; then
    https_write_env_file "$ENV_FILE" "$PUBLIC_HTTPS_URL"
    if grep -q '^PUBLIC_DOMAIN=' "$ENV_FILE" 2>/dev/null; then
        sed -i "s|^PUBLIC_DOMAIN=.*|PUBLIC_DOMAIN=$PUBLIC_DOMAIN|" "$ENV_FILE"
    else
        echo "PUBLIC_DOMAIN=$PUBLIC_DOMAIN" >> "$ENV_FILE"
    fi
    grep -q '^APP_PORT=' "$ENV_FILE" || echo "APP_PORT=8080" >> "$ENV_FILE"
else
    echo "HTTPS: не задан (передайте домен: ./deploy_predicts.sh your.domain.com)"
    grep -q '^BOT_WEBAPP_URL=' "$ENV_FILE" || echo "BOT_WEBAPP_URL=" >> "$ENV_FILE"
fi
chmod 600 "$ENV_FILE"

echo "[4/7] Maven build..."
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx384m"
mvn -B -DskipTests package
echo "Build finished at $(date)"

echo "[5/7] Install systemd units..."
install -m 644 "$APP_DIR/deploy/predicts.service" /etc/systemd/system/predicts.service
systemctl daemon-reload
systemctl enable "$SERVICE"

echo "[6/7] HTTPS (Caddy)..."
if [[ -n "${PUBLIC_DOMAIN:-}" ]]; then
    bash "$APP_DIR/deploy/setup-https.sh"
else
    echo "Skip Caddy (PUBLIC_DOMAIN not configured)"
fi

echo "[7/7] Restart application..."
systemctl restart "$SERVICE"
sleep 3
systemctl --no-pager status "$SERVICE"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE" 2>/dev/null || true
    if [[ -n "${BOT_WEBAPP_URL:-}" ]]; then
        echo ""
        echo "Mini App: $BOT_WEBAPP_URL"
    fi
fi

echo ""
echo "=========================================="
echo "Deploy complete at $(date)"
echo "=========================================="
echo "Last 30 log lines:"
tail -n 30 "$APP_DIR/logs/server-${TODAY}.log" 2>/dev/null || journalctl -u "$SERVICE" -n 30 --no-pager
