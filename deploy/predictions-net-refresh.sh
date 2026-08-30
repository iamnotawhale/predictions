#!/bin/bash
# DynDNS + UPnP на primary (Odyssey). Устанавливается в /usr/local/bin/predictions-net-refresh.sh
set -euo pipefail
# shellcheck disable=SC1091
source /home/nikita/predictions/deploy/duckdns.env

# Не использовать ipify/ifconfig: с домашней сети они могут вернуть IP VPS (VPN/маршрутизация).
IP="${PUBLIC_IP:-}"
if [[ -z "$IP" ]]; then
  IP=$(upnpc -s 2>/dev/null | awk -F= '/ExternalIPAddress/ {gsub(/ /, "", $2); print $2; exit}' || true)
fi
[[ -n "$IP" ]] || { echo "predictions-net-refresh: PUBLIC_IP not set" >&2; exit 1; }

# DuckDNS без &ip= подставляет «текущий» IP клиента — с Odyssey/VPN это может быть VPS.
curl -sf "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${IP}" >/dev/null || true

# Sanity: не оставлять домен на VPS, если primary — Odyssey
if command -v dig >/dev/null 2>&1; then
  resolved=$(dig +short "${DUCKDNS_FULL:-${DUCKDNS_DOMAIN}.duckdns.org}" @8.8.8.8 | head -1)
  if [[ -n "$resolved" && "$resolved" != "$IP" ]]; then
    echo "predictions-net-refresh: WARN DuckDNS ${DUCKDNS_FULL}=${resolved}, expected ${IP}" >&2
  fi
fi
upnpc -a 192.168.1.38 443 443 TCP >/dev/null 2>&1 || true
upnpc -a 192.168.1.38 22 2222 TCP >/dev/null 2>&1 || true
