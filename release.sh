#!/usr/bin/env bash
cd "$(dirname "$0")"
./gradlew assembleRelease
apksigner sign --ks ../my-debug.jks --ks-key-alias my-key --ks-pass "pass:$1" --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false --out my-app-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
