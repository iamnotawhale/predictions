#!/usr/bin/env bash
# Bundle EPL crests into the jar (src/main/resources/static/img/teams/{publicId}.png).
# TeamLogoCacheService / CardLogos prefer these before any CDN.
# Sources ESPN CDN only (same ids as EspnTeamLogos). Run once per season, then commit PNGs.
#
# Usage: scripts/fetch-team-logos.sh
set -u
OUT="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/static/img/teams"
# publicId:espnId — ARS AST BOU BRE BRI CHE CRY EVE FUL LEE LIV MCI MUN NEW NOT SUN TOT WES
PAIRS=(
  42:359 66:362 35:349 55:337 51:331 49:363 52:384 45:368 36:370
  63:357 40:364 50:382 33:360 34:361 65:393 746:366 47:367 48:371
)
mkdir -p "$OUT"
ok=0; fail=0
for pair in "${PAIRS[@]}"; do
  id="${pair%%:*}"
  espn="${pair##*:}"
  url="https://a.espncdn.com/i/teamlogos/soccer/500/${espn}.png"
  if curl -fsS -m 15 -A "Mozilla/5.0" -o "$OUT/$id.png.tmp" "$url" \
      && [ -s "$OUT/$id.png.tmp" ]; then
    mv "$OUT/$id.png.tmp" "$OUT/$id.png"; ok=$((ok+1)); echo "ok   $id (espn $espn)"
  else
    rm -f "$OUT/$id.png.tmp"; fail=$((fail+1)); echo "FAIL $id (espn $espn)" >&2
  fi
done
echo "downloaded=$ok failed=$fail -> $OUT"
[ "$fail" -eq 0 ]
