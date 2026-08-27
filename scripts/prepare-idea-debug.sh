#!/bin/bash
# Подготовка HTTPS URL для one-click Debug в IntelliJ IDEA.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f deploy/local.env ]]; then
    set -a
    # shellcheck disable=SC1091
    source deploy/local.env
    set +a
fi

bash "$ROOT/scripts/dev-https.sh" 8080 --no-local-check

if [[ -f deploy/local-https.env ]]; then
    set -a
    # shellcheck disable=SC1091
    source deploy/local-https.env
    set +a
fi

if [[ -z "${BOT_TOKEN:-}" ]]; then
    echo "[prepare-idea-debug] WARN: BOT_TOKEN not set (put it in deploy/local.env)"
fi

if [[ -n "${BOT_TOKEN:-}" && -n "${BOT_WEBAPP_URL:-}" ]]; then
    export BOT_TOKEN BOT_WEBAPP_URL
    python3 - <<'PY'
import json
import os
import urllib.request
import urllib.parse

token = os.environ.get("BOT_TOKEN", "").strip()
url = os.environ.get("BOT_WEBAPP_URL", "").strip()

if not token or not url:
    raise SystemExit(0)

menu_button = json.dumps(
    {
        "type": "web_app",
        "text": "Открыть приложение",
        "web_app": {"url": url},
    },
    ensure_ascii=False,
)
payload = urllib.parse.urlencode({"menu_button": menu_button}).encode("utf-8")
req = urllib.request.Request(
    f"https://api.telegram.org/bot{token}/setChatMenuButton",
    data=payload,
    headers={"Content-Type": "application/x-www-form-urlencoded"},
)
with urllib.request.urlopen(req, timeout=15) as response:
    body = json.loads(response.read().decode("utf-8"))
    if not body.get("ok"):
        raise RuntimeError(f"setChatMenuButton failed: {body}")

print(f"Telegram menu button updated: {url}")
PY
fi
