#!/bin/bash
# Установка Caddy для прода (HTTPS :443 → Spring :8080).
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
ENV_FILE="${ENV_FILE:-$APP_DIR/deploy/predicts.env}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Skip HTTPS setup: $ENV_FILE not found"
    exit 0
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

if [[ -z "${PUBLIC_DOMAIN:-}" ]]; then
    echo "Skip HTTPS setup: PUBLIC_DOMAIN is not set in $ENV_FILE"
    exit 0
fi

export APP_PORT="${APP_PORT:-8080}"

if ! command -v caddy >/dev/null 2>&1; then
    echo "Installing Caddy..."
    apt-get update -qq
    apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -qq
    apt-get install -y caddy
fi

echo "Configuring Caddy for $PUBLIC_DOMAIN → 127.0.0.1:$APP_PORT"
install -d /etc/caddy
install -m 644 "$APP_DIR/deploy/Caddyfile" /etc/caddy/Caddyfile

id caddy &>/dev/null || useradd --system --home /var/lib/caddy --shell /usr/sbin/nologin caddy
chown -R caddy:caddy /etc/caddy

install -m 644 "$APP_DIR/deploy/caddy.service" /etc/systemd/system/caddy.service
systemctl daemon-reload
systemctl enable caddy
systemctl restart caddy
systemctl --no-pager status caddy || true

echo "HTTPS: https://$PUBLIC_DOMAIN/miniapp/"
