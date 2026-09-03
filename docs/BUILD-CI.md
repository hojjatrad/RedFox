name: Build RedFox

on:
  push:
    branches: [ main, master, redfox ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version: '1.22'

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable
        with:
          targets: aarch64-linux-android,armv7-linux-androideabi

      - name: Install cargo-ndk and gomobile
        run: |
          cargo install cargo-ndk
          go install golang.org/x/mobile/cmd/gomobile@latest
          gomobile init

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: 'platform-tools build-tools;36.0.0 platforms;android-36 ndk;26.3.11579264 cmake;3.22.1'

      - name: Make scripts executable
        run: chmod +x ./gradlew ./core/build-android.sh ./core/xray-mobile/build-aar.sh

      # --- RedFox Xray core: build libxray.aar and fetch geo data files ---
      - name: Build Xray-core AAR
        env:
          ANDROID_NDK_HOME: ${{ env.ANDROID_HOME }}/ndk/26.3.11579264
        run: ./core/xray-mobile/build-aar.sh

      - name: Tor binaries (optional)
        continue-on-error: true
        run: |
          # Tor/Psiphon are optional fallbacks. The native Tor binaries are not
          # required for Xray to function; skip silently when unavailable.
          mkdir -p app/src/main/jniLibs/arm64-v8a app/src/main/jniLibs/armeabi-v7a
          echo "Tor binaries skipped (Xray build is unaffected)."

      - name: Build debug APK
        env:
          ANDROID_HOME: ${{ env.ANDROID_HOME }}
          ANDROID_SDK_ROOT: ${{ env.ANDROID_HOME }}
        run: ./gradlew assembleDebug -PtargetAbi=arm64-v8a,armeabi-v7a --no-daemon

      - name: Read version
        id: ver
        run: |
          V=$(grep -m1 'versionName' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
          echo "name=$V" >> "$GITHUB_OUTPUT"

      - name: Stage APKs
        run: |
          mkdir -p out
          V="${{ steps.ver.outputs.name }}"
          for f in app/build/outputs/apk/*/*.apk; do
            [ -e "$f" ] || continue
            case "$f" in
              *arm64-v8a*)   ABI=arm64-v8a ;;
              *armeabi-v7a*) ABI=armeabi-v7a ;;
              *)             ABI=universal ;;
            esac
            cp "$f" "out/RedFox-v$V-$ABI.apk"
          done
          (cd out && sha256sum *.apk > SHA256SUMS.txt)
          ls -la out

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: RedFox
          path: out/*

      - name: Publish GitHub Release (tags only)
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v2
        with:
          files: out/*
          generate_release_notes: true
