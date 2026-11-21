#!/usr/bin/env bash
# Bundle EPL crests into the jar:
#   static/img/teams/{publicId}.png
#   static/img/teams/by-code/{CODE}.png
# TeamLogoCacheService / CardLogos prefer these before any CDN.
# Clubs with a FotMob id use FotMob first; everyone else uses ESPN.
# Rejects duplicate crest bytes across codes so a wrong CDN id cannot
# overwrite a good file. Run once per season, then commit PNGs.
#
# Usage: scripts/fetch-team-logos.sh
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/static/img/teams"
BY_CODE="$OUT/by-code"
mkdir -p "$OUT" "$BY_CODE"

# publicId:espnId:CODE[:fotmobId]
PAIRS=(
  42:359:ARS
  66:362:AST
  35:349:BOU
  55:337:BRE
  51:331:BRI
  49:363:CHE
  1346:388:COV
  52:384:CRY
  45:368:EVE
  36:370:FUL
  64:306:HUL:8667
  57:373:IPS:9902
  63:357:LEE
  40:364:LIV
  50:382:MCI
  33:360:MUN
  34:361:NEW
  65:393:NOT
  746:366:SUN
  47:367:TOT
  48:371:WES
)

download_to() {
  local url="$1" dest="$2"
  curl -fsS -m 45 -A "Mozilla/5.0" -o "$dest" "$url" && [ -s "$dest" ]
}

ok=0; fail=0
declare -A SEEN_MD5=()
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

for pair in "${PAIRS[@]}"; do
  IFS=':' read -r id espn code fotmob <<< "$pair"
  espn_url="https://a.espncdn.com/i/teamlogos/soccer/500/${espn}.png"
  tmp="$TMPDIR/$code.png"
  src=""
  if [ -n "${fotmob:-}" ] \
      && download_to "https://images.fotmob.com/image_resources/logo/teamlogo/${fotmob}.png" "$tmp"; then
    src="fotmob $fotmob"
  elif download_to "$espn_url" "$tmp"; then
    src="espn $espn"
  else
    fail=$((fail + 1))
    echo "FAIL $code ($id espn $espn fotmob ${fotmob:-none})" >&2
    continue
  fi
  md5=$(md5sum "$tmp" | awk '{print $1}')
  if [ -n "${SEEN_MD5[$md5]:-}" ]; then
    echo "FAIL $code duplicate crest of ${SEEN_MD5[$md5]} (md5=$md5) — left existing files untouched" >&2
    fail=$((fail + 1))
    continue
  fi
  SEEN_MD5[$md5]=$code
  cp -f "$tmp" "$OUT/$id.png"
  cp -f "$tmp" "$BY_CODE/$code.png"
  ok=$((ok + 1))
  echo "ok   $code ($id via $src) $(wc -c < "$tmp")b"
done
echo "downloaded=$ok failed=$fail -> $OUT (+ by-code/)"
[ "$fail" -eq 0 ]
