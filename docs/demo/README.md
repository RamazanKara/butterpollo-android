# RUBYLIGHT debug demo

The non-root debug APK includes an offline marketing fixture mode. Package IDs stay
`com.butterpollo.client` / `com.butterpollo.client.root`. All demo activities,
fixtures, scripted metrics, PNG artwork and MP4 samples live in `app/src/debug`;
release variants do not compile or package them. Normal app launches still use
the real computer manager and streaming connection.

## Build

`local.properties` and the native submodule must be present (copy the missing
files from `C:\src\bp-T5` if needed). Build from the repository root:

```powershell
$env:JAVA_HOME = 'C:\jdk17'
.\gradlew.bat :app:assembleNonRootDebug :app:testNonRootDebugUnitTest :app:lintNonRootDebug --no-daemon --max-workers=2
```

APK: `app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`.
No extra flavor or dependency is required. If the build user's home is read-only,
point `GRADLE_USER_HOME` and `ANDROID_USER_HOME` at writable local directories.

## Launch on the caller's device

Install that APK on an English-language Android device/emulator. The caller runs
adb; these instructions and scripts do not start an emulator.

```powershell
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
adb shell am start -n com.butterpollo.client/com.limelight.demo.DemoLauncherActivity
adb shell am start -n com.butterpollo.client/com.limelight.demo.DemoLauncherActivity --es state library
```

| `state` extra | Screen |
| --- | --- |
| `hosts` (default) | Real PcView cards: Living-room PC online/paired, Studio PC online/unpaired, Laptop offline with Wake PC action |
| `library` | Real AppView, ten original 3:4 covers, Racing Game marked running |
| `stream`, `compact` | Looping sample in the Game layout's StreamView with compact performance overlay |
| `advanced` | Same sample with advanced overlay |
| `touch` | Same sample with the real on-screen controller and its layout editor |
| `pip` | Sample ready for PiP; press Home to enter it |
| `settings` | Real settings and presets, with isolated demo preferences |
| `controller` | Real controller mapping screen, populated with a demo controller |

Tap Living-room PC to enter its library; tap any title to play the sample.
Long-press the performance overlay to switch compact/advanced. Tap the stream
or its background for overlay and touch-control actions. Studio pairing and
Laptop Wake PC are offline demonstration actions; no pairing request or magic
packet is sent. The fixture binder never discovers or polls hosts and creates
no launcher shortcuts. Preferences use `demo_` names and cached art stays under
`cache/demo`; the user's computer database and normal preferences are untouched.
Force-stop the app and open its normal launcher to leave the demo.

The network decoder requires a real host handshake, so demo playback uses
MediaPlayer in the same `activity_game` / `StreamView` surface container. The
production `PerfOverlayListener`, `PerformanceOverlay` formatters, Material
ActionSheet and `VirtualController` render its UI. Supported AV1/HEVC samples
are preferred, with H.264 fallback. PiP is Android's actual floating video window
(Android 8+ with PiP support), not a composited image.

**All performance readings are scripted illustrations, not measurements.** The
sample is 1080p60 SDR; the displayed 119.9 FPS, approximately 6 ms decode, 0.1%
loss, AV1 10-bit HDR and FSR 1 profile are synthetic. The stream carries a small
DEMO label. Keep that distinction in published captions; use real-host footage
for claims about measured performance, HDR or upscaling quality.

## Capture stills and the guided tour

Use an unlocked device with platform-tools on PATH (or in `ANDROID_HOME`). Set
`ANDROID_SERIAL` when more than one device is connected. PiP must be enabled for
the app. No host or physical controller is needed.

```powershell
.\scripts\demo\capture-stills.ps1
.\scripts\demo\capture-video.ps1
# Optional retakes, using the existing shot IDs:
.\scripts\demo\capture-video.ps1 -Shot 04-library,05-stream,12-pip
```

The scripts temporarily set 1080x2400, density 420 and portrait rotation, then
restore the prior overrides in `finally`. They force-stop only this package
between states and at exit. They do not clear app data. Settings navigation and
the library/stream tour taps use UIAutomator resource IDs, text and discovered
bounds; missing UI stops capture and preserves `out/demo/capture/last-ui.xml`.
PiP capture checks that Android actually entered pinned window mode.

Stills go to `docs/screenshots/demo/`: `pc-list.png`, `library.png`, `stream.png`,
`stream-compact.png`, `stream-advanced.png`, `settings-presets.png`,
`upscaling-options.png`, `controller.png`, `touch-controller.png`, `pip.png`.
PNG dimensions are checked after pulling. These files are created by the caller;
no empty-app placeholders are substituted.

`capture-video.ps1` records all thirteen [shots](shotlist.md) with
`adb shell screenrecord --size 1080x2400 --bit-rate 20000000`. Clips go to
`out/demo/clips/<shot-id>.mp4`, with one second of handles at each end. Shot 04
taps into the library and shot 05 taps Racing Game while recording. Shot 12
presses Home while recording. Logs stay beside the clips. Recording is silent.
The older `capture.ps1` entry point forwards to this offline tour; its `-NoHost`
option is now redundant.

The existing edit remains available after capture:

```powershell
.\scripts\demo\render.ps1 -Storyboard
.\scripts\demo\render.ps1
```

It uses the shotlist's new demo stills or the matching recorded clips and writes
landscape/portrait exports, posters, review frames and `sources.json` under
`out/demo/`. Review the outputs before using them in README/site/video assets.
Stream shots preserve the real 16:9 surface inside the portrait viewport.

## Regenerate bundled assets

```powershell
$env:JAVA_HOME = 'C:\jdk17'
.\scripts\demo\generate-assets.ps1
```

The generator finds FFmpeg on PATH or under
`%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_*\*\bin`; it downloads
nothing. `GenerateAssets.java` uses JDK 17 Java2D and system Segoe UI fonts to
make ten ruby/slate gradient covers and an original ten-second seamless flight
animation: parallax mountains, a neon ground grid, towers, particles, a moving
ship and HUD. Raw frames are piped to FFmpeg at 1920x1080/60. H.264 is mandatory;
HEVC and AV1 10-bit SDR copies are encoded when their software encoders are
available. All footage and art are synthetic and contain no third-party games.
The PNG preview and ffprobe verification reports go to `out/demo/assets/`.
Only the generated `app/src/debug/assets/demo/` files ship in the debug APK.
