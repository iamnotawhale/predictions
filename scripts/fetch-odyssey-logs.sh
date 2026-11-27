#!/usr/bin/env bash
# Скачать хвост логов predicts с Odyssey на эту машину.
#
#   ./scripts/fetch-odyssey-logs.sh
#   ./scripts/fetch-odyssey-logs.sh 300
#   ./scripts/fetch-odyssey-logs.sh 200 --since "1 hour ago"
#   OUT=./tmp ./scripts/fetch-odyssey-logs.sh 500
#
# Env: SSH_HOST (default odyssey), UNIT (default predicts.service), OUT (default .)
set -euo pipefail

LINES="${1:-200}"
shift || true
EXTRA=("$@")

SSH_HOST="${SSH_HOST:-odyssey}"
UNIT="${UNIT:-predicts.service}"
OUT_DIR="${OUT:-.}"
mkdir -p "$OUT_DIR"

if ! [[ "$LINES" =~ ^[0-9]+$ ]]; then
  echo "usage: $0 [lines] [--since …]" >&2
  exit 2
fi

TS="$(date +%Y%m%d-%H%M%S)"
FILE="${OUT_DIR%/}/predicts-${TS}.log"

REMOTE=(journalctl -u "$UNIT" -n "$LINES" --no-pager -o short-iso)
if ((${#EXTRA[@]})); then
  REMOTE+=("${EXTRA[@]}")
fi

ssh -o BatchMode=yes "$SSH_HOST" "$(printf '%q ' "${REMOTE[@]}")" >"$FILE"
BYTES="$(wc -c <"$FILE" | tr -d ' ')"
echo "wrote $FILE ($BYTES bytes, last $LINES of $UNIT via $SSH_HOST)"
