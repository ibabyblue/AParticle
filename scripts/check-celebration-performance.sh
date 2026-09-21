#!/usr/bin/env bash
# Runs the real hardware Canvas path (about 15s, plus install and compilation).
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :benchmark:assembleRelease --offline
adb_bin="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
if [[ ! -x "$adb_bin" ]]; then adb_bin="adb"; fi
backend="${1:-gpu}"
if [[ "$backend" != "gpu" && "$backend" != "canvas" ]]; then echo "Expected gpu or canvas" >&2; exit 2; fi
output="benchmark/build/reports/celebration-performance-$backend.txt"
mkdir -p "$(dirname "$output")"
"$adb_bin" install -r benchmark/build/outputs/apk/release/benchmark-release.apk
# Fix ART compilation mode so before/after comparisons do not depend on JIT history.
"$adb_bin" shell cmd package compile -m speed -f com.ibabyblue.aparticle.benchmark
"$adb_bin" shell dumpsys package com.ibabyblue.aparticle.benchmark > "benchmark/build/reports/compilation.txt"
if ! grep -q 'status=speed]' "benchmark/build/reports/compilation.txt"; then
  echo 'ART speed compilation was not applied; refusing a misleading comparison.' >&2
  exit 1
fi
"$adb_bin" shell am instrument -w -e backend "$backend" \
  com.ibabyblue.aparticle.benchmark/com.ibabyblue.aparticle.internal.CelebrationBenchmark | tee "$output"
# am instrument may exit with status zero even when the instrumentation gate fails.
if ! grep -q 'FRAME_BUDGET=PASS' "$output"; then
  echo 'Celebration performance gate failed; see the frame timings above.' >&2
  exit 1
fi
