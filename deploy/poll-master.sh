#!/usr/bin/env bash
set -uo pipefail

REPO=iamnotawhale/predictions
STATE=$HOME/.local/state/predicts-build
SRC=$HOME/predictions-src
APP_PATHS="src pom.xml application.yml application-prod.yml"
mkdir -p "$STATE"

log() { echo "$(date -Iseconds) [poll-master] $*"; }

MODE=${1:-run}

exec 8>"$STATE/poll.lock"
flock -n 8 || { log "poll already running"; exit 0; }

exec 7>"$STATE/git.lock"
flock 7
git -C "$SRC" fetch -q origin master || { log "fetch failed, will retry"; exit 0; }
exec 7>&-

runs=$(curl -sS -m 30 -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$REPO/actions/workflows/ci.yml/runs?branch=master&event=push&status=success&per_page=10") || { log "api failed"; exit 0; }
candidate=$(printf '%s' "$runs" | python3 -c '
import json, sys
for r in json.load(sys.stdin).get("workflow_runs", []):
    print(r["head_sha"]); break
')
[ -n "$candidate" ] || { log "no successful CI run yet"; exit 0; }

installed=$(cat "$STATE/installed" 2>/dev/null || true)
failed=$(cat "$STATE/failed" 2>/dev/null || true)
log "candidate=$candidate installed=${installed:-none} failed=${failed:-none}"
if [ "$candidate" = "$installed" ] || [ "$candidate" = "$failed" ]; then
  log "nothing new"; exit 0
fi

if [ -n "$installed" ] && git -C "$SRC" cat-file -e "$installed^{commit}" 2>/dev/null; then
  if git -C "$SRC" merge-base --is-ancestor "$candidate" "$installed"; then
    log "candidate is older than installed, refusing to downgrade"; exit 0
  fi
  if git -C "$SRC" diff --quiet "$installed" "$candidate" -- $APP_PATHS; then
    log "no application changes since $installed, marking $candidate installed"
    [ "$MODE" = "--dry-run" ] || echo "$candidate" > "$STATE/installed"
    exit 0
  fi
fi

if [ "$MODE" = "--dry-run" ]; then
  log "dry-run: would request build of $candidate"; exit 0
fi
log "requesting build of $candidate"
echo "$candidate" > "$STATE/requested"
sudo -n systemctl start --no-block predicts-deploy.service
