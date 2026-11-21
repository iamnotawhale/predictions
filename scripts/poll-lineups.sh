#!/bin/bash
# Deprecated: lineups come from ESPN summary inside the app.
# Kept so old cron entries exit cleanly without calling API-Football.
set -euo pipefail
echo "$(date '+%Y-%m-%d %H:%M:%S %Z') SKIP: poll-lineups.sh retired (use ESPN lineups in app)" >&2
exit 0
