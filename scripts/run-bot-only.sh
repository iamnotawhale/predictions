#!/bin/bash
# Локальный запуск только бота (без cloudflared). Логи: tail -f logs/server-$(date +%Y-%m-%d).log
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f target/predictions-1.0.0.jar ]]; then
    echo "Сборка..."
    mvn -q package -DskipTests
fi

fuser -k 8080/tcp 2>/dev/null || true
sleep 1

config_locations() {
    local loc="file:./application.yml,file:./application-local.yml"
    [[ -f deploy/local.env ]] && loc="$loc,file:./deploy/local.env"
    echo "$loc"
}

echo "Бот: @eplinfodevbot (не @eplinfobot)"
echo "Лог приложения: tail -f logs/server-$(date +%Y-%m-%d).log"
echo "(не перенаправляйте stdout в этот файл — иначе строки [server] снова задвоятся)"
echo ""

exec java -Duser.timezone=Europe/Moscow -jar target/predictions-1.0.0.jar \
    --spring.config.location="$(config_locations)" \
    --spring.profiles.active=local
