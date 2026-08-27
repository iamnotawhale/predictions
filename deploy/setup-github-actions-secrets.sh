#!/bin/bash
# Заполнить GitHub Actions secrets для deploy-prod.yml (нужен: gh auth login).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEY="$ROOT/deploy/ci/odyssey_github_actions"
[[ -f "$KEY" ]] || { echo "Missing $KEY — generate CI key first"; exit 1; }
[[ -f "$ROOT/deploy/deploy.env" ]] && source "$ROOT/deploy/deploy.env"
[[ -f "$ROOT/deploy/duckdns.env" ]] && source "$ROOT/deploy/duckdns.env"

: "${DEPLOY_APP_DIR:?}"
HOST="${PROD_HOST:-${DUCKDNS_FULL:-predicts.duckdns.org}}"
PORT="${PROD_SSH_PORT:-2222}"
USER="${PROD_USER:-${DEPLOY_SERVER%%@*}}"

command -v gh >/dev/null || { echo "Install GitHub CLI: https://cli.github.com/"; exit 1; }
gh auth status >/dev/null

gh secret set PROD_SSH_PRIVATE_KEY < "$KEY"
gh secret set PROD_HOST -b "$HOST"
gh secret set PROD_SSH_PORT -b "$PORT"
gh secret set PROD_USER -b "$USER"
gh secret set PROD_APP_DIR -b "$DEPLOY_APP_DIR"
echo "Secrets set for $(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo "Host=$HOST Port=$PORT User=$USER AppDir=$DEPLOY_APP_DIR"
