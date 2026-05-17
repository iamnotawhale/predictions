#!/bin/bash
# Проверка часового пояса: симулируем сервер (UTC+2) и сравниваем со старым/новым поведением.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mvn -q compile -DskipTests

# MUN–NOT 17.05.2026: 12:30 BST = 11:30 UTC = 14:30 MSK
KICKOFF_UTC="2026-05-17 11:30:00 UTC"
TS=$(date -d "$KICKOFF_UTC" +%s)

TMPDIR_VERIFY=$(mktemp -d)
cat > "$TMPDIR_VERIFY/TzCheck.java" <<'JAVA'
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import zhigalin.predictions.util.AppTimeZones;

public class TzCheck {
    public static void main(String[] args) {
        long ts = Long.parseLong(args[0]);
        ZoneId jvm = ZoneId.systemDefault();
        LocalDateTime legacy = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), TimeZone.getDefault().toZoneId());
        LocalDateTime fixed = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), AppTimeZones.DISPLAY);
        System.out.printf("JVM zone:           %s%n", jvm);
        System.out.printf("Старое (getDefault): %s  ← как на сервере без фикса%n", legacy);
        System.out.printf("Новое (AppTimeZones): %s  ← ожидаем MSK%n", fixed);
    }
}
JAVA

javac -cp target/classes -d "$TMPDIR_VERIFY" "$TMPDIR_VERIFY/TzCheck.java"

echo "=========================================="
echo "Кикофф в UTC: $KICKOFF_UTC → ожидаем 14:30 MSK"
echo "=========================================="
echo ""
echo ">>> Симуляция сервера (TZ=Europe/Berlin, UTC+2 летом)"
TZ=Europe/Berlin java -cp "target/classes:$TMPDIR_VERIFY" TzCheck "$TS"
echo ""
echo ">>> С фиксом systemd (TZ=Europe/Moscow + -Duser.timezone)"
TZ=Europe/Moscow java -Duser.timezone=Europe/Moscow -cp "target/classes:$TMPDIR_VERIFY" TzCheck "$TS"
echo ""
echo ">>> Spring scheduling time-zone из application.yml"
grep -A1 'scheduling:' application.yml | head -5
grep 'time-zone' application.yml || true

rm -rf "$TMPDIR_VERIFY"
echo ""
echo "Готово."
