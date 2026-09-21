#!/usr/bin/env bash
# Requires a connected Android 14+ device with the benchmark page kept in front.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :benchmark:assembleRelease --offline
adb_bin="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
if [[ ! -x "$adb_bin" ]]; then adb_bin="adb"; fi
output="benchmark/build/reports/celebration-visual.txt"
mkdir -p "$(dirname "$output")"
"$adb_bin" install -r benchmark/build/outputs/apk/release/benchmark-release.apk
"$adb_bin" shell am instrument -w -e visual true \
  com.ibabyblue.aparticle.benchmark/com.ibabyblue.aparticle.internal.CelebrationBenchmark | tee "$output"
if ! grep -q 'VISUAL_BUDGET=PASS' "$output"; then
  echo 'Celebration visual comparison failed; see the pixel differences above.' >&2
  exit 1
fi
"$adb_bin" pull /sdcard/Android/data/com.ibabyblue.aparticle.benchmark/files/celebration-visual \
  benchmark/build/reports/
