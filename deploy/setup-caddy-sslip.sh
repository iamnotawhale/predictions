#!/bin/bash
# Постоянный HTTPS для Mini App без trycloudflare (порт 8443 — 443 часто занят xray/VPN).
set -euo pipefail

APP_DIR="${APP_DIR:-/home/predictions}"
ENV_FILE="${ENV_FILE:-$APP_DIR/deploy/predicts.env}"

# shellcheck source=../scripts/https-env.sh
source "$APP_DIR/scripts/https-env.sh"

PUBLIC_IP="${PUBLIC_IP:-$(curl -sf --max-time 5 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')}"
DOMAIN="${PUBLIC_DOMAIN:-${PUBLIC_IP//./-}.sslip.io}"
HTTPS_PORT="${HTTPS_PORT:-8443}"
BASE_URL="https://${DOMAIN}:${HTTPS_PORT}"
WEBAPP_URL="$(https_derive_webapp_url "$BASE_URL")"

if ! command -v caddy >/dev/null 2>&1; then
    echo "Installing Caddy..."
    apt-get update -qq
    apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -qq
    apt-get install -y caddy
fi

cat > /etc/caddy/Caddyfile <<EOF
${DOMAIN}:${HTTPS_PORT} {
    reverse_proxy 127.0.0.1:${APP_PORT:-8080}
}
EOF

systemctl enable caddy
systemctl restart caddy

mkdir -p "$(dirname "$ENV_FILE")"
touch "$ENV_FILE"
https_write_env_file "$ENV_FILE" "$BASE_URL"
if grep -q '^PUBLIC_DOMAIN=' "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^PUBLIC_DOMAIN=.*|PUBLIC_DOMAIN=$DOMAIN|" "$ENV_FILE"
else
    echo "PUBLIC_DOMAIN=$DOMAIN" >> "$ENV_FILE"
fi
grep -q '^HTTPS_PORT=' "$ENV_FILE" || echo "HTTPS_PORT=$HTTPS_PORT" >> "$ENV_FILE"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  if [[ -n "${BOT_TOKEN:-}" ]]; then
    python3 -c "
import json, os, urllib.request
t=os.environ['BOT_TOKEN']
u='$WEBAPP_URL'
p=json.dumps({'menu_button':{'type':'web_app','text':'Открыть приложение','web_app':{'url':u}}}).encode()
r=urllib.request.Request(f'https://api.telegram.org/bot{t}/setChatMenuButton',data=p,headers={'Content-Type':'application/json'})
print(urllib.request.urlopen(r,timeout=15).read().decode())
"
  fi
fi

systemctl disable --now predictions-tunnel 2>/dev/null || true
echo "Mini App: $WEBAPP_URL"
echo "Проверка: curl -skI ${WEBAPP_URL}"
