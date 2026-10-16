import pathlib
W = pathlib.Path(".github/workflows/build-debug.yml")
s = W.read_text(encoding="utf-8")
if "x11-engine:" in s:
    print("CI: sudah")
    raise SystemExit
s = s.rstrip("\n") + """

  x11-engine:
    runs-on: ubuntu-latest
    timeout-minutes: 120
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - name: Alat build native X server
        run: |
          sudo apt-get update
          sudo apt-get install -y bison flex python3 patch pkg-config
      - name: Unduh dependensi X server (SHA terkunci)
        run: bash tools/ci-x11-deps.sh
      - name: Install NDK 29 dan CMake
        run: |
          SDKMANAGER="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKMANAGER" --licenses > /dev/null || true
          "$SDKMANAGER" --install "ndk;29.0.14206865" "cmake;3.22.1"
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"
      - name: Build modul X11 (AAR)
        run: gradle :engines:termux-x11-lorie:assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: x11-engine-aar
          path: engines/termux-x11/lorie/build/outputs/aar/*.aar
          if-no-files-found: error
"""
W.write_text(s, encoding="utf-8")
print("CI: ok")
