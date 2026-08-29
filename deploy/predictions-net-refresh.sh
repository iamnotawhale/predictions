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
[[ -n "$IP" ]] || exit 0

curl -sf "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${IP}" >/dev/null || true
upnpc -a 192.168.1.38 443 443 TCP >/dev/null 2>&1 || true
upnpc -a 192.168.1.38 22 2222 TCP >/dev/null 2>&1 || true
