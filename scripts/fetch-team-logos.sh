#!/usr/bin/env bash
# Bundle EPL crests into the jar (src/main/resources/static/img/teams/{apiFootballId}.png).
# TeamLogoCacheService falls back to these when every CDN is unreachable, so crests never disappear.
# Run once per season from a machine with internet access, then commit the PNGs.
#
# Usage: scripts/fetch-team-logos.sh [id ...]   (default: current API-Football EPL club ids)
set -u
OUT="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/static/img/teams"
IDS=("$@")
if [ ${#IDS[@]} -eq 0 ]; then
  # ARS AST BOU BRE BHA BUR CHE CRY EVE FUL LEE LIV MCI MUN NEW NFO SUN TOT WHU WOL
  IDS=(42 66 35 55 51 44 49 52 45 36 63 40 50 33 34 65 746 47 48 39)
fi
mkdir -p "$OUT"
ok=0; fail=0
for id in "${IDS[@]}"; do
  if curl -fsS -m 15 -A "Mozilla/5.0" -o "$OUT/$id.png.tmp" "https://media.api-sports.io/football/teams/$id.png" \
      && [ -s "$OUT/$id.png.tmp" ]; then
    mv "$OUT/$id.png.tmp" "$OUT/$id.png"; ok=$((ok+1)); echo "ok   $id"
  else
    rm -f "$OUT/$id.png.tmp"; fail=$((fail+1)); echo "FAIL $id" >&2
  fi
done
echo "downloaded=$ok failed=$fail -> $OUT"
[ "$fail" -eq 0 ]
