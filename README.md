# Butterpollo Android

Butterpollo Android is a latency-focused client for [Butterpollo](https://github.com/RamazanKara/Butterpollo),
based on Moonlight Android (GPL-3.0), with stock Sunshine and Apollo compatibility.

- Per-frame latency overlay/CSV and configurable Android low-latency controls.
- Native-resolution, high-refresh virtual-display requests with host render scale.
- HEVC/AV1 HDR10 with display metadata; capability-gated YUV 4:4:4 with 4:2:0 fallback.
- Experimental PyroWave is an explicit codec choice on compatible Vulkan devices; Auto keeps H.264/HEVC/AV1.
  PyroWave needs a fast LAN and much higher bitrate (about 280 Mbps to start at 720p/60).
- Host processing avg/p95/p99 beside client latency, and persistent per-device identity.
- Foreground text clipboard transfer, permission-aware host actions and encrypted server commands.
- A stream menu for disconnect/resume, device permissions, host frame-limiter status and runtime bitrate.
- Butterpollo branding and display, latency, and codec settings groups; conservative 720p/60,
  automatic codec selection and low-latency decoding defaults.

See [the parity audit and device-testing checklist](docs/BUTTERPOLLO_PARITY.md) for PyroWave feasibility
findings, supported host extras and known limits.

## Install

1. Download the `butterpollo-debug` artifact from a successful
   [Android debug workflow](https://github.com/RamazanKara/butterpollo-android/actions/workflows/android.yml)
   and extract `app-nonRoot-debug.apk`, or build it below. Android 5.0 or later is required; no root needed.
2. Open the APK on your Android device and allow installation from your file manager when prompted,
   or run `adb install -r app-nonRoot-debug.apk` with USB debugging enabled.
3. Start [Butterpollo](https://github.com/RamazanKara/Butterpollo) on your PC. Select the discovered
   host (or use **+** to enter its address), then enter the displayed PIN in **Devices** in the host
   web console. Grant this device the desired list, launch and input permissions there.
4. Launch an app. Android **Back**, or **Ctrl+Alt+Shift+M** on a keyboard, opens the stream menu.
   Clipboard transfer is plain text, explicitly initiated, and requires an active session plus the
   corresponding host permission. Server commands are the host's configured commands; sending one
   does not confirm its execution. **Pause stream / disconnect** leaves the host application running;
   select the same app to resume. It does not suspend the game process.

Local release builds produce an unsigned APK. **Unsigned APKs cannot be installed directly.**
Copy it to `butterpollo-unsigned.apk`, then put JDK 17 and Android SDK Build Tools on your PATH:

```sh
keytool -genkeypair -keystore butterpollo.keystore -alias butterpollo -keyalg RSA -keysize 3072 -validity 10000
zipalign -P 16 -f 4 butterpollo-unsigned.apk butterpollo-aligned.apk
apksigner sign --ks butterpollo.keystore --ks-key-alias butterpollo --out butterpollo.apk butterpollo-aligned.apk
apksigner verify --verbose butterpollo.apk
adb install -r butterpollo.apk
```

Create the keystore once and keep it for updates; tools prompt for its password. See Android's
[APK signing instructions](https://developer.android.com/tools/apksigner). CI has no signing secrets.
Debug builds from different CI runs may use different keys; switching keys requires uninstalling
the old app (which removes its settings and pairing identity). Butterpollo installs alongside Moonlight.

## Building

Install JDK 17, Android SDK Platform 37 and NDK `29.0.14206865` through Android Studio/SDK Manager,
accept the SDK licenses, and set `ANDROID_HOME` (or `sdk.dir` in untracked `local.properties`).
On Windows, use PowerShell and the Windows toolchains:

```powershell
git submodule update --init --recursive --jobs 2
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat --no-daemon --max-workers=2 :app:assembleNonRootDebug :app:lintNonRootDebug :app:testNonRootDebugUnitTest
```

The debug APK is `app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`. Use
`:app:assembleNonRootRelease` for the unsigned release APK under `app/build/outputs/apk/nonRoot/release/`.
On Linux/macOS use `bash gradlew` with the same arguments. Native compilation and unit test forks
are capped at two. The single GitHub Actions job builds debug, runs lint, unit tests and native parser
tests on pushes to `main` and manual dispatch. It does not publish releases. Protocol fixtures are
synthetic; the parity document lists required real-device checks.

After building debug, run `python scripts/emulator-smoke.py` with Python 3, the SDK above and the
`sunset` Android 35 AVD installed. It refuses to start alongside another emulator, uses a headless,
disposable AVD session, installs the APK, manually adds a loopback server-info fixture, opens settings
and checks the production latency overlay layout in a debug-only screen, including rotation.
Screenshots go to `docs/screenshots/` (half size, palette PNG when Pillow is installed); UI dumps and logcat go to `app/build/emulator-smoke/`.
The emulator is stopped in a `finally` block. The fixture does not pair or stream; overlay timings
remain unavailable. The debug overlay activity is absent from release builds.

## Upstream Moonlight

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).

Apollo extension behavior was cross-checked against [Artemis Android](https://github.com/ClassicOldSong/moonlight-android)
by ClassicOldSong and contributors (GPL-3.0), with Butterpollo Rust protocol differences handled explicitly.
Moonlight attribution and the [GPL-3.0 license](LICENSE.txt) are retained.
