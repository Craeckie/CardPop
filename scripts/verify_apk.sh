#!/usr/bin/env bash
# Verify the Hilt application class is correctly transformed in an APK.
# Usage: verify_apk.sh <apk>
set -e
DEXDUMP=/workspace/Android/sdk/build-tools/36.0.0/dexdump
APK="$1"
TMP=$(mktemp -d)
unzip -q "$APK" 'classes*.dex' -d "$TMP"

app_defs=0
hilt_defs=0
super=""
for d in "$TMP"/*.dex; do
  a=$("$DEXDUMP" "$d" 2>/dev/null | grep -c "Class descriptor  : 'Lcom/cardpop/app/FloatingLearningApplication;'") || true
  h=$("$DEXDUMP" "$d" 2>/dev/null | grep -c "Class descriptor  : 'Lcom/cardpop/app/Hilt_FloatingLearningApplication;'") || true
  app_defs=$((app_defs + a))
  hilt_defs=$((hilt_defs + h))
  if [ "$a" -gt 0 ]; then
    s=$("$DEXDUMP" "$d" 2>/dev/null | grep -A2 "Class descriptor  : 'Lcom/cardpop/app/FloatingLearningApplication;'" | grep "Superclass" | head -1)
    super="$super [$(basename "$d"):$s]"
  fi
done

echo "APK: $APK"
echo "  FloatingLearningApplication definitions: $app_defs  (expected 1)"
echo "  Hilt_FloatingLearningApplication defs:   $hilt_defs  (expected 1)"
echo "  FloatingLearningApplication superclass:$super"
echo "  (expected superclass: Hilt_FloatingLearningApplication)"
rm -rf "$TMP"
