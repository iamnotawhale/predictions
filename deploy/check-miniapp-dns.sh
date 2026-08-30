#!/usr/bin/env bash
# Проверка split-DNS для Mini App в домашней сети.
set -euo pipefail

DOMAIN="${MINIAPP_DOMAIN:-predicts.duckdns.org}"
LAN_IP="${ODYSSEY_LAN_IP:-192.168.1.38}"
URL="https://${DOMAIN}/miniapp/"

resolve() {
  if command -v dig >/dev/null 2>&1; then
    dig +short "$DOMAIN" A | head -1
  else
    getent ahosts "$DOMAIN" | awk '/STREAM/ {print $1; exit}'
  fi
}

IP="$(resolve || true)"
echo "DNS ${DOMAIN} -> ${IP:-<empty>}"

if [[ "$IP" == "$LAN_IP" ]]; then
  echo "OK: split-DNS указывает на Odyssey (${LAN_IP})"
elif [[ -n "$IP" ]]; then
  echo "WARN: ожидался ${LAN_IP}, получен ${IP}"
  echo "      С домашней Wi‑Fi miniapp может не открыться."
  echo "      См. deploy/keenetic-split-dns.md"
else
  echo "WARN: не удалось разрешить ${DOMAIN}"
fi

if curl -fsS -o /dev/null -w "HTTP %{http_code}\n" --connect-timeout 5 "$URL"; then
  :
else
  code=$?
  echo "FAIL: ${URL} недоступен (curl exit ${code})"
  exit 1
fi
