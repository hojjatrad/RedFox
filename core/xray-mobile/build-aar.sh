#!/usr/bin/env bash
# Build the Xray-core mobile AAR (libxray.aar) for Android.
#
# Output: app/libs/xray.aar  (referenced by app/build.gradle.kts as implementation(fileTree(...)))
#
# Requires: Go >= 1.22, Android NDK, and gomobile. The GitHub Actions workflow
# (.github/workflows/build.yml) installs these automatically; run this locally
# with the same toolchain if you build outside CI.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_LIBS="$(cd "$SCRIPT_DIR/../../app" && pwd)/libs"
mkdir -p "$APP_LIBS"

# gomobile setup
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/26.3.11579264}"
export GOPATH="${GOPATH:-$HOME/go}"
export PATH="$PATH:$GOPATH/bin"

if ! command -v gomobile >/dev/null 2>&1; then
  echo "Installing gomobile…"
  go install golang.org/x/mobile/cmd/gomobile@latest
  gomobile init
fi

cd "$SCRIPT_DIR"
echo "Fetching Xray-core…"
go mod tidy || true

echo "Building libxray.aar (this takes several minutes on first run)…"
gomobile bind -target=android/arm64,android/arm \
  -androidapi 26 \
  -trimpath \
  -o "$APP_LIBS/xray.aar" \
  .

# Xray needs geoip.dat/geosite.dat for geoip:ir / geosite:* routing rules.
# Download them into app/src/main/assets/xray/ so the Kotlin side copies them.
ASSETS_DIR="$(cd "$SCRIPT_DIR/../../app/src/main" && pwd)/assets/xray"
mkdir -p "$ASSETS_DIR"
for f in geoip.dat geosite.dat; do
  if [ ! -f "$ASSETS_DIR/$f" ]; then
    echo "Downloading $f…"
    curl -fsSL -o "$ASSETS_DIR/$f" "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/$f"
  fi
done

echo "Done: $APP_LIBS/xray.aar"
