# Butterpollo Android parity — milestones 3–5

Initially audited on 2026-10-07 at `f144a731`; milestone 5 rechecked the client extras against a
fresh shallow, unmodified checkout of
[Butterpollo `f06e72c73c1960665d031d27f8dad02202bca56e`](https://github.com/RamazanKara/Butterpollo/tree/f06e72c73c1960665d031d27f8dad02202bca56e).
The current Rust host is authoritative; the retained C++ implementation is historical.
“Supported” below means implemented protocol paths, not a real-device streaming certification.

| Capability | Host support | Client status |
| --- | --- | --- |
| Discovery, addresses, ports, wake | mDNS, IPv4/IPv6, `hostname`, `LocalIP`, `mac`, `HttpsPort`, `ExternalPort` | Existing discovery, manual addresses, port handling and Wake-on-LAN. |
| Pairing and device identity | PIN/certificate pairing, `PairStatus`, host `uniqueid`; device UUID bound to certificate | Existing pairing and certificate pinning. Now sends the persisted installation ID instead of a shared constant, the Android model as device name, and authenticated HTTPS unpair when paired. |
| One-time PIN, device permissions | OTP via admin API; `Permission`, per-device enable and access masks | Butterpollo-labelled PIN pairing. Unsigned permission masks displayed and refreshed before host actions; denied viewing/quit and input-disabled sessions explained. Missing fields preserve stock behavior; malformed masks grant nothing. OTP entry remains unimplemented; use ordinary PIN pairing. |
| App list and artwork | `/applist`, `/appasset`; `AppTitle`, `ID`, `IsHdrSupported`, `UUID`, `IDX`, `ArtVersion`, permission-filtered entries | Existing ID-based launch, HDR hint and cover download; fixed parsing of whitespace/text outside app entries. UUID launch, host ordering and artwork version invalidation are deferred Apollo extras. Unknown fields remain compatible. |
| Launch, resume, quit and ownership | `/launch`, `/resume`, `/cancel`, `currentgame`, `currentgameuuid`, `state`, `gamesession`, `sessionUrl0` | Back / Ctrl+Alt+Shift+M opens the stream menu. Pause disconnects without `/cancel`; the host app keeps running and selecting it again uses `/resume`. View or launch permission allows viewing. Display parameters apply to launch/resume alike. UUID ownership UI remains unimplemented. |
| Per-app/per-device settings | Host merges stream/input/display/encoder overrides, prep/undo commands, `APOLLO_*` environment | Applied by host on normal client launches. Editing profiles is an authenticated web-console operation, not `/applist`; native management deferred. |
| Per-device virtual display | `VirtualDisplayCapable`, `VirtualDisplayDriverReady`, `virtualDisplay=1`; certificate-derived stable display identity | New opt-in request, sent only when advertised and driver not reported unavailable; otherwise a notice and host policy fallback. Shared/off/layout/forced-output policy stays host controlled. |
| Native resolution and render scale | `mode=WIDTHxHEIGHTxRATE`, SDP viewport, `scaleFactor` | Existing native portrait/landscape/fullscreen choices pass their exact dimensions. New 50–200% host render scale, default 100%. Stream viewport is unchanged by render scale. |
| High/fractional refresh | Integer/decimal launch rate, SDP `maxFPS`, `clientRefreshRateX100` | High rates remain uncapped on Sunshine-family hosts. Butterpollo receives the selected display's fractional rate to 0.01 Hz when it matches the requested FPS; other hosts retain integer launch syntax. |
| Desktop DPI, layout, mode overrides | Host `dd_virtual_display_scale`, per-device `display_mode`, layouts, HDR profiles, retained displays | Host configured; no client DPI field. `scaleFactor` scales render pixels, **not** Android density or Windows text size. Host/app overrides can supersede requested geometry/rate. |
| H.264 / HEVC / AV1 SDR | `ServerCodecModeSupport`, RTSP codec markers | Existing hardware MediaCodec paths and software-decoder exclusions retained. Stock Sunshine/Apollo/GFE fallback retained. |
| HEVC / AV1 HDR10 | Main10 flags, launch `hdrMode`, SDP `dynamicRangeMode`; ten-bit BT.2020/PQ | Negotiates a common HDR codec, preferring HDR over a higher-priority SDR codec. Requires HDR10 display and HDR-capable decoder. HDR10+ decoder profiles also qualify for static HDR10; no dynamic HDR10+ claim. Auto can discover AV1 for HDR. |
| Display/HDR metadata | Control type `0x010e`: enabled plus little-endian RGB/white primaries, mastering luminance, MaxCLL/MaxFALL, optional full-frame luminance | Converts to Android's 25-byte `KEY_HDR_STATIC_INFO`, preserves units, omits unsupported trailing full-frame field. Explicit BT.2020/PQ hints, HDR/SDR transitions including absent metadata, SurfaceView output; malformed short metadata ignored. |
| Ten-bit SDR | Host `prefer_sdr_10bit` changes an HDR-capable stream to SDR and sends HDR-off | Existing ten-bit decoding plus corrected HDR-off handling. No separately advertised client ten-bit-SDR request; host policy supplies it. |
| Standard YUV 4:4:4 | Sunshine codec bits `0x40000`–`0x400000`, SDP `x-ss-video[0].chromaSamplingType`; encoder dependent | New opt-in AVC High 4:4:4 8-bit / HEVC Main444 8-bit (API 37 profile) on decoders advertising those profiles and supporting the requested size/rate. Raw AVC 4:4:4 SPS preserved. Negotiation falls back to 4:2:0 with a notice and actual chroma in the overlay. HDR takes priority. |
| HEVC/AV1 ten-bit 4:4:4, AV1 eight-bit 4:4:4 | Encoder-dependent standard flags; Radeon principally offers HDR 4:4:4 through PyroWave | No standard Android profile identifiers for these combinations in SDK 37; never inferred from Main10 or output color formats. Uses supported 4:2:0 instead. PyroWave can supply these combinations on qualified Vulkan devices; see milestone 4b below. |
| Host frame timing | Video short header carries unsigned processing duration in 100 µs units; zero means unavailable | Now beside client latency as avg/p95/p99 over 600 observed frames, also `host_processing_ms` in per-frame CSV. Host claim-to-packet duration is not encode-only or end-to-end latency. No clock subtraction. |
| Capture/encode/frame-age breakdown | Detailed timing in host diagnostics/web API; not separately carried in standard video packets | Not fabricated from processing duration; admin telemetry UI deferred. |
| Audio | Opus stereo, 5.1, 7.1, quality/channel-map negotiation, local playback, encryption | Existing audio negotiation/playback. Endpoint selection remains host configured. |
| Transport and recovery | RTSP/encrypted RTSP, UDP video/audio, reliable encrypted control, FEC, keyframe recovery, optional reference invalidation, ping/connect-data extensions, QoS | Existing pinned moonlight-common-c `874ac954` handles these. Slice/reference negotiation and milestone 2 latency controls retained. |
| Keyboard/mouse/touch/pen | Standard input plus `x-ss-general.featureFlags` pen/touch bit | Existing keyboard, mouse, stylus and protocol capability fallback. Native finger-touch dispatch remains disabled upstream in favor of touch-as-mouse; deferred input UX. |
| Controllers and feedback | Multiple pads, touch bit, motion, battery, rumble/triggers, LED; driver-dependent DualSense effects | Existing controller arrival/touch/motion/battery and rumble/trigger/LED paths. Host exposes one touchpad; DualSense adaptive effects/secondary-pad extensions are not exposed by this client's core and are deferred. |
| Frame limiter and VRR | `FrameLimiterSupported`, `FrameLimiterEnabled`, `VirtualDisplayFrameLimiterEnabled`, `FrameLimiterFpsLimitMilliHz`; `vrr` aliases and SDP `vrrLowLatency`, 1000 Hz virtual mode | Stream menu reports manual/virtual limiter state and fractional configured cap (zero means stream rate). Host defaults and selected Android pacing apply. Nonary VRR is not negotiated: this MediaCodec client has no equivalent variable-cadence presentation path. Fixed-rate high refresh does not claim VRR. |
| Runtime bitrate / ABR | `/bitrate`, `/api/abr/capabilities`: client-driven runtime bitrate, `supported:false` for host ABR | Butterpollo stream-menu adjustment uses kbps and displays the applied host cap; current-session only, with refreshed view/launch permission. Other hosts retain startup bitrate selection. Client automatic bitrate control is not implemented. |
| Remote monitor/input and control tiles | Synthetic app IDs/UUIDs, `remote_monitor`, `input_only`, resume/disconnect/terminate/replace actions | Tiles can be listed by existing app parser; role-specific lifecycle/confirmation UI deferred. Not claimed as full remote-session support. |
| Clipboard and server commands | Permission-scoped text `/actions/clipboard`, advertised `ServerCommand` names | Explicit foreground text send/receive over paired HTTPS with direction permissions, UTF-8, 1 MiB limit and no content logging. Commands use reliable encrypted control type `0x3000`, original 8-bit index and host-specific payload size; confirmation, permission refresh and one-second spacing. Host enforces app restrictions; no execution acknowledgment exists. |
| PyroWave | Codec/SDP flags, bitstream ID, adaptive FEC/records, `PyroWaveHostLinkMbps`, bandwidth probe bytes/endpoint | Vulkan decoder/presentation port, including ten-bit HDR and 4:4:4. Auto codec + SurfaceView only, runtime feature/surface checks and a completed warm-up before advertising. Exact `186f0393` bitstream and ordinary packet-container transport; HEVC/AV1/H.264 fallback retained. Adaptive records, bandwidth probing and real-device certification remain unimplemented/unverified. See milestone 4b. |
| Host diagnostics/version | `appversion`, `GfeVersion`, `MaxLumaPixelsHEVC`, `RustHostVersion`; loopback-only session/pending/app/profile fields | Standard version/codec checks retained; Rust version gates decimal launch rates. Local diagnostics ignored safely. |
| Library and host administration | Steam/Playnite sync, Lossless Scaling, RTSS/RTX HDR/TrueHDR, display/HDR profiles, settings, devices, logs, updates, auth/tokens | Host-side effects work with ordinary launches; administration remains in the web console, not a streaming-client parity requirement. No admin credentials added to pairing. |

Source: host [protocol handlers](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/rust/host/src/nvhttp.rs),
[RTSP](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/rust/core/src/rtsp.rs),
[display policy](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/rust/host/src/display_session.rs),
[HDR layout](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/rust/core/src/hdr.rs),
[compatibility audit](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/rust/PARITY.md),
and [admin API](https://github.com/RamazanKara/Butterpollo/blob/f144a731b1e82a790b918600ffdc73aed7273080/docs/api.md).
Android limits: [codec profiles](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecProfileLevel)
and [MediaFormat HDR/color keys](https://developer.android.com/reference/android/media/MediaFormat).

## Milestone 5: extras and packaging

The latest host was cloned with `git clone --depth 1` outside this checkout and was only read.
Sources: [HTTP/permissions/clipboard/bitrate](https://github.com/RamazanKara/Butterpollo/blob/f06e72c73c1960665d031d27f8dad02202bca56e/rust/host/src/nvhttp.rs),
[encrypted server commands](https://github.com/RamazanKara/Butterpollo/blob/f06e72c73c1960665d031d27f8dad02202bca56e/rust/host/src/stream.rs),
[pause status](https://github.com/RamazanKara/Butterpollo/blob/f06e72c73c1960665d031d27f8dad02202bca56e/rust/host/src/web.rs),
and [limiter policy](https://github.com/RamazanKara/Butterpollo/blob/f06e72c73c1960665d031d27f8dad02202bca56e/docs/configuration.md).
Artemis Android was inspected at
[`c5cf27f4`](https://github.com/ClassicOldSong/moonlight-android/tree/c5cf27f4dc822db0e863c4691e7a70c74bea977a),
with common-c [`c9994368`](https://github.com/ClassicOldSong/moonlight-common-c/blob/c999436858471dfefa7617af3b7dc03ec1644ce4/src/ControlStream.c).
Its GPL-3.0 license permits reuse. This implementation follows its protocol approach, retains
Moonlight attribution, and leaves the pinned common-c submodule unchanged.

Butterpollo Rust requires exactly one command-index byte; Apollo uses that byte plus three zeros.
A local native translation unit shares the core's encryption, sequence numbering, ENet mutex and
reliable delivery. Empty command names preserve their index. Commands are refreshed before sending;
changed names/indices require reopening the menu. The UI reports sending, not successful execution.

Clipboard transfer is manual and text-only; no polling or automatic export. Incoming text is only
installed while foreground and focused, and marked sensitive on supported Android versions.
Non-text, oversized, HTTP-error and XML error-page replies cannot replace the clipboard. Android/OEM
clipboard services may have a smaller limit than the host's 1 MiB. Pause disconnects the stream,
leaving the application running; there is no client `/pause` endpoint or process-suspension claim.
Runtime bitrate is exposed only for `RustHostVersion` hosts, without probing stock-host endpoints.

Settings now group Display, Latency, and Codec and color, preserving keys, values and hardware gates.
Defaults remain 720p/60, auto codec, minimum-latency pacing, supported decoder low-latency hints,
SurfaceView, and opt-in HDR/4:4:4/virtual displays. Butterpollo has a vector/adaptive icon and the
existing separate `com.butterpollo.client` package. README covers debug install and local release signing.
The two Ubuntu/JDK 17 workflows use two Gradle workers/native jobs and 15-minute timeouts: debug +
tests on `main`/manual dispatch; unsigned release + tests and release attachment on `v*` tags.
No signing secrets are used. GitHub-hosted execution needs its first published run; nothing was pushed.

Local verification passed on Windows/JDK 17 with Gradle `--no-daemon --max-workers=2`, a 1.5 GiB
heap and two active processors: `assembleNonRootDebug`, `testNonRootDebugUnitTest` (42 tests,
zero failures/errors/skips), `assembleNonRootRelease`, and `lintNonRootDebug` (zero errors,
201 warnings). Both APKs contain ARM64, ARMv7, x86 and x86-64 native command bindings.
The debug signature verifies; release signature verification fails as expected for an unsigned
APK, and its 16 KiB zip alignment passes. Workflow YAML/triggers and preservation of every
preference key/value/default were checked. The root flavor and real-device behavior were not tested.

Milestone 5 real-device checks, in addition to the earlier streaming checklist below:

- Install debug and locally signed release APKs on Android 5+, current Android and Android TV.
  Inspect legacy/adaptive/themed icons, pairing/discovery text, settings and HDR filtering; verify
  same-key updates retain settings/pairing and Butterpollo coexists with Moonlight.
- Pair with Butterpollo, Sunshine and Apollo. Exercise independent list/view/launch/input masks,
  zero permissions, revocation and re-enabling; check input-disabled notices and quit restrictions.
  Check ordinary and synthetic resume tiles. Full remote-monitor/input-only lifecycle UX remains deferred.
- Open, navigate and dismiss the menu with touch Back, TV remote and keyboard shortcut. Verify
  capture release/restoration, controller input, PiP suppression, backgrounding, rotation, and
  disconnect during an HTTP action without late dialogs or clipboard writes.
- Transfer Unicode, multiline and empty text in both directions; test independent permissions,
  non-text clips, size/OEM limits, clipboard contention, denied/expired sessions, sensitive previews,
  and absence of clipboard contents in logcat.
- Run commands at indices 0, 1 and 255 on Butterpollo and Apollo, including unnamed entries,
  reordered lists, permission/app-level denial, rapid repeats, packet loss and disconnect. Verify
  exactly the selected command runs; there is no host execution acknowledgment.
- Change runtime bitrate and verify encoder rate/host caps, failure after session termination,
  and restored startup settings on reconnect. Compare displayed limiter state and fractional caps
  with the host console; Android pacing must remain unchanged and no VRR request should be sent.
- Disconnect and resume the same running game, including background/foreground and rotation.
  Verify the game stays running and no unintended `/cancel` occurs.

## Milestone 4b: Vulkan decoder port

The second attempt ports the Android renderer from
[joemossjr16/artemis-android-pyrowave `387d3a5c`](https://github.com/joemossjr16/artemis-android-pyrowave/tree/387d3a5ce1e3df8d4a4d29eec5b81a7b904926a5).
Its [LICENSE.txt](https://github.com/joemossjr16/artemis-android-pyrowave/blob/387d3a5ce1e3df8d4a4d29eec5b81a7b904926a5/LICENSE.txt)
is GPL-3.0, compatible with this GPL-3.0 Moonlight-derived client. Attribution remains in source,
the application license, and packaged `assets/pyrowave_notices.txt`. PyroWave, Granite and volk's
full MIT notices are included there. The necessary new runtime dependency is `libpyrowave-shared.so`.
Both ARM64 and x86-64 libraries were rebuilt with Windows NDK 29, rather than copied from the fork.
32-bit APKs retain the ordinary decoders and tolerate the absent Vulkan library.

Pinned dependency sources: PyroWave `186f0393b77f7755953b5ecde994bb1cec2e4155`, Granite
`b6cffd5ce81f540f0855e6778428483e14763d9b`, volk `47cddf7ed97b94118a08aacb548a411188e016cc`,
Vulkan-Headers `6802bb4733b63ed5efd3adb308a6c885ef180ea1`. The decoder short-block patch from
Butterpollo `6772d401` is retained in `jni/pyrowave-renderer/patches/`. Sources/headers are MIT;
the small Android renderer and common-c integration remain GPL-3.0.

### Negotiation and output

- No new preference: **Auto** offers PyroWave when using **SurfaceView**. Existing HDR and 4:4:4
  preferences control its profile bits; explicit H.264/HEVC/AV1 and TextureView retain MediaCodec.
  This is the call made without an operator available. The ordinary codec offers remain available.
- Android API 29+, a 64-bit library and Vulkan **1.3** are necessary. Runtime checks include
  subgroup basic/vote/ballot/arithmetic/shuffle/shuffle-relative operations, compute stage and size
  control (4–128), full subgroups, shaderInt16, storageBuffer8BitAccess, storageBuffer16BitAccess,
  timelineSemaphore, synchronization2 and shaderStorageImageWriteWithoutFormat. Float16 arithmetic
  is optional. Checks also cover graphics+compute+present queue support, scratch image formats,
  dimensions, layers and workgroup limits. These are conservative requirements of this port.
- After server capability intersection, the client creates the decoder, swapchain and colour pipeline
  on the **actual stream Surface**, then executes a zero-coefficient grey frame into the decode planes.
  This warm-up is not presented or included in stream statistics. A failed profile tries the next
  compatible profile; failure of all PyroWave profiles leaves conventional codec negotiation intact.
  The prepared renderer is reused by video setup and released when RTSP selects a conventional codec.
- Native RTSP requires `PYROWAVE/90000` and the exact `x-ss-pyrowave.bitstream:186f0393` attribute.
  Missing/different IDs select AV1/HEVC/H.264. SDP sends `bitStreamFormat=3`, the selected chroma and
  HDR flags. It deliberately omits **both** `pyrowaveAdaptiveFec` and `pyrowaveFeatures`; even a zero
  adaptive-FEC attribute selects a different framing protocol on Butterpollo.
- The ordinary core FEC/depacketizer supplies complete opaque frames with its eight-byte short
  header and trailing FEC padding removed. The renderer accepts little-endian packet count/lengths,
  validates exact boundaries and block sizes, then feeds the packets to the pinned decoder. It does
  not accept the fork's older `PYRW` container or partially recovered record-mode frames. Corrupt
  frames are discarded, and the next complete intra frame recovers. The core submodule remains pinned;
  `android_rtsp.c` and `android_sdp.c` are attributed copies of its two modified translation units.
- Eight-bit output uses R8_UNORM planes; ten-bit output uses **R16_UNORM** throughout the decoded
  planes, including full-size chroma for 4:4:4. The shader matches Butterpollo's code/1023 encoding,
  64–940 luma, midpoint 512/scale 896 chroma, optional full range, centred 4:2:0, and BT.709 SDR or
  BT.2020 non-constant-luminance HDR. HDR RGB remains PQ encoded, with no sRGB framebuffer conversion.
- HDR requires an A2B10G10R10 or A2R10G10B10 UNORM + `HDR10_ST2084_EXT` surface pair,
  `VK_EXT_swapchain_colorspace`, and `VK_EXT_hdr_metadata`. The same ten-bit format must also support
  SDR presentation, preserving ten-bit SDR when the host turns HDR off. Mode changes recreate the
  swapchain. Mastering primaries/white point, luminance and MaxCLL/MaxFALL are passed with Vulkan units;
  absent/short metadata clears old values to unknown. The unsupported trailing full-frame field is
  omitted. [Vulkan metadata units](https://docs.vulkan.org/refpages/latest/refpages/source/VkHdrMetadataEXT.html)
  and [colour-space definitions](https://docs.vulkan.org/refpages/latest/refpages/source/VkColorSpaceKHR.html)
  govern this path; MediaCodec HDR keys are not used for the Vulkan surface.
- Decode has its own fence, including when swapchain acquisition times out or requires recreation.
  PyroWave disables direct submission so GPU/display waits run on the core's decoder thread.
  Its CLOCK_MONOTONIC completion feeds milestone 1 `FrameLatencyStats` and the existing CSV/overlay.
  Input-to-output includes parsing/upload/queue wait and GPU completion, but excludes subsequent
  image acquisition/present. Separate GPU timestamps bracket the GPU decode. Render/scanout time
  stays unavailable; no fabricated render callback or rendered-FPS count. Fatal runtime decoder
  errors end the connection; they do not attempt an in-place codec switch. Reconnect with explicit
  HEVC/AV1 for a driver failure occurring after successful preparation.

### Measured synthetic frames

On 2026-10-07, `app/src/test/native/pyrowave_benchmark.cpp` used the pinned upstream Windows encoder
to generate 1280×720 luma ramps/chroma checkerboards, packetized at 1024 bytes into the same container
the Android renderer accepts. The two frames were 501,996 (4:2:0) and 502,656 (4:4:4) bytes, with a
500,000-byte codec budget. Each of four runs discarded 10 warm-up decodes and measured 60 completed
decodes; submissions were spaced by at least 17 ms and the process was restricted to two CPUs.

The GPU selected by the upstream C API was **AMD Radeon RX 7900 XT**, Vulkan 1.4.349, raw driver
version 8389003. This was the existing Windows toolchain build of the same pinned C API; DLL SHA-256
`090423bf0054353f29b3cf6ac7cfab76a59cdc7bec369ae8c0564fdb825f8a31`.
`PyroWaveBenchmark.java` feeds the native monotonic input/completed-output timestamps into the actual
milestone 1 statistics class. The recorded input is checked in at
`app/src/test/resources/pyrowave/windows-720p.csv`; the adapter can regenerate all four milestone 1 CSVs.

| 720p, 8-bit | Completed frames | Input → output avg | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| 4:2:0 compute | 60 | 15.850740 ms | 44.936700 ms | 110.844200 ms |
| 4:2:0 fragment | 60 | 34.978430 ms | 135.435400 ms | 203.041700 ms |
| 4:4:4 compute | 60 | 26.796847 ms | 93.904900 ms | 126.660700 ms |
| 4:4:4 fragment | 60 | 24.810995 ms | 75.169600 ms | 108.253700 ms |

These are **Windows synchronous GPU decode + CPU readback** timings on a shared, CPU-constrained
machine, with large scheduling/contention tails. They are neither GPU-only nor Android renderer
measurements, and establish no mobile performance target. Receive/render timestamps are left blank.
The zero-coefficient warm-up was separately checked through the upstream compute and fragment paths
for both chroma modes, producing grey pixels. No Android device/emulator was connected; Android
surface output, ten-bit visual accuracy, HDR transitions, live transport and mobile timing remain
unverified. The shaders compile and pass SPIR-V validation; that does not certify display output.

To repeat the Windows benchmark from this repository in PowerShell (MSYS2 UCRT64 compiler, Windows
executables; use a matching C API installation in `$PyroInstall`):

```powershell
$Bench = Join-Path $env:TEMP 'bp-pyrowave-bench'
New-Item -ItemType Directory -Force $Bench | Out-Null
$PyroInstall = 'C:/Users/ramaz/git/pyrowave-build/install-186f0393'
$VulkanHeaders = 'C:/Users/ramaz/git/pyrowave-build/src-186f0393/Granite/third_party/khronos/vulkan-headers/include'
$env:PATH = 'C:/msys64/ucrt64/bin;' + $env:PATH
g++ -std=c++17 -O2 app/src/test/native/pyrowave_frame_test.cpp -o "$Bench/frame-test.exe"
& "$Bench/frame-test.exe"
g++ -std=c++17 -O2 -Iapp/src/main/jni/pyrowave-renderer/prebuilt/include "-I$VulkanHeaders" app/src/test/native/pyrowave_benchmark.cpp "$PyroInstall/lib/libpyrowave-shared.dll.a" -o "$Bench/benchmark.exe"
Copy-Item "$PyroInstall/bin/libpyrowave-shared-0.dll" $Bench
$p = Start-Process "$Bench/benchmark.exe" -WorkingDirectory $Bench -WindowStyle Hidden -PassThru -RedirectStandardOutput "$Bench/measurements.csv" -RedirectStandardError "$Bench/benchmark.log"
$p.ProcessorAffinity = 3
$p.WaitForExit()
if ($p.ExitCode -ne 0) { throw 'Benchmark failed; inspect benchmark.log' }
javac -J-XX:ActiveProcessorCount=2 -d "$Bench/classes" app/src/main/java/com/limelight/binding/video/FrameLatencyStats.java app/src/test/java/com/limelight/binding/video/PyroWaveBenchmark.java
java -XX:ActiveProcessorCount=2 -cp "$Bench/classes" com.limelight.binding.video.PyroWaveBenchmark "$Bench/measurements.csv" $Bench
```

### Rebuilding the bundled decoder

Run from the repository root. Use a fresh temporary directory; normal Gradle builds consume the
committed prebuilts, so no downloads or CMake build run during ordinary application builds.
No owner checkout, system services or drivers are modified by these commands.

```powershell
$Repo = (Get-Location).Path
$Work = Join-Path $env:TEMP 'bp-pyrowave-rebuild'
$Source = "$Work/pyrowave"
$Sdk = 'C:/Users/ramaz/AppData/Local/Android/Sdk'
$Ndk = "$Sdk/ndk/29.0.14206865"
$env:PATH = "$Sdk/cmake/3.22.1/bin;" + $env:PATH
git init $Source
git -C $Source remote add origin https://github.com/Themaister/pyrowave.git
git -C $Source fetch --depth 1 origin 186f0393b77f7755953b5ecde994bb1cec2e4155
git -C $Source checkout --detach FETCH_HEAD
git init "$Source/Granite"
git -C "$Source/Granite" remote add origin https://github.com/Themaister/Granite.git
git -C "$Source/Granite" fetch --depth 1 origin b6cffd5ce81f540f0855e6778428483e14763d9b
git -C "$Source/Granite" checkout --detach FETCH_HEAD
git -C "$Source/Granite" submodule update --init --jobs 2 third_party/volk third_party/khronos/vulkan-headers
Get-ChildItem "$Repo/app/src/main/jni/pyrowave-renderer/patches/*.patch" | ForEach-Object { git -C $Source apply $_.FullName }
foreach ($abi in @('arm64-v8a', 'x86_64')) {
    cmake -S $Source -B "$Work/$abi" -G Ninja "-DCMAKE_TOOLCHAIN_FILE=$Ndk/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
    cmake --build "$Work/$abi" --target pyrowave-shared --parallel 2
    Copy-Item "$Work/$abi/libpyrowave-shared.so" "app/src/main/jni/pyrowave-renderer/prebuilt/$abi/"
    & "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe" --strip-unneeded "app/src/main/jni/pyrowave-renderer/prebuilt/$abi/libpyrowave-shared.so"
}
```

Both libraries have 16 KiB ELF LOAD alignment and only Android system-library imports. This run's
stripped SHA-256 hashes are `7b32cb6af70584821d55fb70b1b12376bdc53d5c7d19f039eef2abdde3984999`
(ARM64) and `1978dac82d8722c6baf9be240c31bbd739409672c31c234a51cc6f9c1e833f00` (x86-64).
To regenerate `shaders_spv.h`, compile each `shaders/fullscreen.vert` and `shaders/planar_csc.frag`
with NDK `shader-tools/windows-x86_64/glslc.exe -O --target-env=vulkan1.3 -mfmt=c`, and put those
initializer lists in `static const uint32_t fullscreen_vert_spv[]` / `planar_csc_frag_spv[]`.
Also compile binary SPIR-V and run `spirv-val.exe --target-env vulkan1.3` on it.

### Exact real-device checks still required

Local verification passed with Windows JDK 17, the specified SDK, Gradle `--no-daemon --max-workers=2`,
two active processors and a 1.5 GiB heap: `assembleNonRootDebug`, `assembleNonRootRelease`,
`testNonRootDebugUnitTest` (46 tests, zero failures/errors/skips), and `lintNonRootDebug` (zero errors,
201 existing warnings). Both APKs contain all four ordinary-codec ABIs and the two 64-bit PyroWave
ABIs, with the complete notices preserved in release assets. Release 16 KiB zip alignment passed.
The native parser/RTSP assertions and the 240-frame milestone 1 replay passed; the standalone ARM64
benchmark also compiled against the bundled decoder. The root flavor and real-device checks below
were not run.

1. Build with JDK 17, the specified SDK, `gradlew.bat --no-daemon --max-workers=2
   assembleNonRootDebug testNonRootDebugUnitTest`. Native make is already capped at `-j2`.
   Find the APK under `app/build/intermediates/apk/nonRoot/debug/` with AGP 9.4, then
   `adb -s SERIAL install -r app/build/intermediates/apk/nonRoot/debug/app-nonRoot-debug.apk`.
2. On a Vulkan 1.3 Adreno and a Mali/Immortalis device, set Codec **Auto**, disable TextureView,
   enable the performance overlay, and start at 1280×720/60. Pair with Butterpollo built with its
   `186f0393` PyroWave encoder. Use adequate LAN bitrate (e.g. 200,000 kbps on gigabit Ethernet).
   Capture `adb -s SERIAL logcat -v threadtime` to a file during the run. Verify the PyroWave surface
   preparation and selected decode path logs, actual codec/chroma in the overlay, and host SDP
   `bitStreamFormat=3`. Check that neither record-mode attribute is sent.
3. Repeat with 4:4:4 on/off, HDR on/off, full range on/off, and host `prefer_sdr_10bit`. On an HDR10
   panel, display ten-bit grey ramps, black/white patches, PQ highlights and single-pixel red/blue
   text on the host. Confirm the actual SurfaceView's 10-bit ST2084 swapchain in Vulkan validation /
   SurfaceFlinger diagnostics, BT.2020 primaries, no eight-bit intermediate/banding, correct blacks,
   centre chroma and full-resolution colour text. Compare against a known-good host/local image.
   Change/remove mastering/MaxCLL/MaxFALL metadata; toggle host HDR while connected. Confirm no stale
   metadata, a new SDR colour-space swapchain on HDR-off and preserved ten-bit planes.
4. Pause/disconnect/resume, background/foreground, rotate, replace/destroy the surface, and stream
   while the host changes HDR. Verify objects and decode/presentation fences are released, no hangs
   or repeated invalid semaphore use, and no validation-layer errors. Apply temporary packet loss/
   reordering on a test network: complete recovered frames decode; unrecoverable frames drop and
   the next intra frame recovers. Feed truncated/bad packet lengths and duplicate short blocks through
   the native parser regression test; no loops or out-of-bounds reads are permitted.
5. Stop normally to finalize the milestone 1 CSV, then extract it using
   `adb -s SERIAL exec-out run-as com.butterpollo.client cat files/butterpollo-latency.csv > device.csv`.
   During streaming use `files/butterpollo-latency.csv.tmp`. Report input-to-output avg/p95/p99 and
   sample count, plus GPU-decode log/overlay timing separately. Render fields must remain blank with
   `render_unavailable`. Repeat 1080p/native resolution at 60/120/240 Hz where supported, including
   a thermal run; never substitute Windows timings for the device's measurements.
6. Repeat with a 32-bit/API <29/Vulkan <1.3 device, TextureView, explicit HEVC/AV1, a missing-feature
   Vulkan driver, and an HDR surface without the required format/extension pairs. Verify conventional
   fallback, including HDR preference over PyroWave SDR. On a test host remove/change the SDP bitstream
   ID and verify AV1/HEVC fallback; repeat with ordinary Sunshine/Apollo and H.264-only hosts.

For an on-device synthetic C API benchmark independent of a host, compile
`app/src/test/native/pyrowave_benchmark.cpp` with NDK's `aarch64-linux-android29-clang++.cmd`,
`-std=c++17 -O2 -static-libstdc++`, include `jni/pyrowave-renderer/prebuilt/include`, and link with
`-Lapp/src/main/jni/pyrowave-renderer/prebuilt/arm64-v8a -lpyrowave-shared`.
Push the executable and the matching `.so` into `/data/local/tmp/bp-pyrowave/`; run
`adb -s SERIAL shell 'cd /data/local/tmp/bp-pyrowave && chmod 700 benchmark && LD_LIBRARY_PATH=. ./benchmark > measurements.csv'`.
Pull `measurements.csv` and feed it to the Java adapter above, then remove that temporary directory.
This measures complete GPU decode plus readback; the live-stream CSV is the Android Surface renderer
measurement. No instrumentation/emulator or mobile performance result is claimed from this run.

## Milestone 4: first-attempt audit (superseded by 4b)

The following records the first attempt at `c23f1357`; its disabled status and zero-frame
measurement apply to that historical attempt, not the implementation above.


**Mobile decoding is technically plausible and already demonstrated for SDR. This audit does not
establish that mobile GPUs cannot decode PyroWave.** The requested Android Vulkan decoder, full
ten-bit HDR 4:4:4 presentation and per-frame decode benchmark remain unimplemented. The safe result
of this run is an explicit negotiation exclusion, fallback regression tests and the evidence below,
not a completed decoder or an assertion that the task's mobile-infeasibility condition was met.

On 2026-10-07, cloned the latest
[Butterpollo `6772d4019c0837e372b801637f3bf1b57bea2765`](https://github.com/RamazanKara/Butterpollo/tree/6772d4019c0837e372b801637f3bf1b57bea2765)
with `git clone --depth 1` into a temporary directory outside this repository; it was only read.
Also inspected current upstream
[PyroWave `c0b997f84ced7bd827ca737aa5145f4ec811de8d`](https://github.com/Themaister/pyrowave/tree/c0b997f84ced7bd827ca737aa5145f4ec811de8d)
and the exact host compatibility target,
[PyroWave `186f0393b77f7755953b5ecde994bb1cec2e4155`](https://github.com/Themaister/pyrowave/tree/186f0393b77f7755953b5ecde994bb1cec2e4155),
C API 0.6.0, with Granite `b6cffd5ce81f540f0855e6778428483e14763d9b`.
The host [build recipe](https://github.com/RamazanKara/Butterpollo/blob/6772d4019c0837e372b801637f3bf1b57bea2765/scripts/build_pyrowave.sh)
pins those revisions and three patches, including rejection of short decoder blocks. Any port must
retain that fix: a duplicate block with zero payload length can otherwise leave the parse cursor
stationary indefinitely.

License is not the blocker. Hans-Kristian Arntzen's
[PyroWave MIT license](https://github.com/Themaister/pyrowave/blob/186f0393b77f7755953b5ecde994bb1cec2e4155/LICENSE)
and [Granite MIT license](https://github.com/Themaister/Granite/blob/b6cffd5ce81f540f0855e6778428483e14763d9b/LICENSE)
allow reuse with their copyright and permission notices retained; the host also retains volk's notice.
The Android reference port retains GPL-3.0. No decoder code, prebuilts or new dependencies are vendored
here, and Moonlight's GPL-3.0 license and attribution remain intact.

### What the sources establish

- **Existing mobile implementation:**
  [joemossjr16's Android results](https://github.com/joemossjr16/pyrowave-streaming) report about 5.7 ms
  GPU decode at 1972×1248 on Snapdragon 8 Elite Gen 5 / Adreno 840, with FP16 and the fragment path.
  This is an upstream measurement, not a measurement from this run, and does not certify HDR 4:4:4.
  The inspected [Artemis port at `387d3a5c`](https://github.com/joemossjr16/artemis-android-pyrowave/tree/387d3a5ce1e3df8d4a4d29eec5b81a7b904926a5)
  explicitly refuses HDR in `Game.java`, uses `R8_UNORM` decode planes, and its
  [colour shader](https://github.com/joemossjr16/artemis-android-pyrowave/blob/387d3a5ce1e3df8d4a4d29eec5b81a7b904926a5/app/src/main/jni/pyrowave-renderer/shaders/planar_csc.frag)
  implements eight-bit limited-range BT.709. Importing that renderer unchanged would lose the
  requested precision and misinterpret HDR colour.
- **Vulkan requirements:** the pinned
  [C API](https://github.com/Themaister/pyrowave/blob/186f0393b77f7755953b5ecde994bb1cec2e4155/pyrowave.h)
  and [decoder](https://github.com/Themaister/pyrowave/blob/186f0393b77f7755953b5ecde994bb1cec2e4155/pyrowave_decoder.cpp)
  require subgroup basic/vote/ballot/arithmetic/shuffle/shuffle-relative operations and compatible
  subgroup size control (wave4–128). Vulkan version alone is insufficient. The reference Android
  probe additionally checks shaderInt16, storageBuffer8BitAccess, timelineSemaphore,
  computeFullSubgroups and synchronization2. Those checks are conservative: upstream
  [common code](https://github.com/Themaister/pyrowave/blob/186f0393b77f7755953b5ecde994bb1cec2e4155/pyrowave_common.cpp)
  has a texel-buffer alternative to eight-bit storage when the device's texel-buffer limit is large
  enough; shaderFloat16 is optional. Missing one reference-port feature is not proof that every
  upstream decoder path is impossible. Upstream prefers fragment iDWT on proprietary Qualcomm
  drivers; a compute-only port cannot assume desktop performance on mobile.
- **Current wire format:** host
  [RTSP](https://github.com/RamazanKara/Butterpollo/blob/6772d4019c0837e372b801637f3bf1b57bea2765/rust/core/src/rtsp.rs)
  advertises `PYROWAVE/90000` and bitstream ID `186f0393`; selecting it requires
  `x-nv-vqos[0].bitStreamFormat=3`. The current
  [framing code](https://github.com/RamazanKara/Butterpollo/blob/6772d4019c0837e372b801637f3bf1b57bea2765/rust/core/src/pyrowave.rs)
  uses a little-endian packet count followed by little-endian lengths and codec packets, or record
  framing when negotiated. This differs from the reference Android renderer's big-endian `PYRW`
  version-1 container. Presence of `pyrowaveAdaptiveFec`, **even with value 0**, selects records;
  `pyrowaveFeatures & 1` also selects them. A port must agree on framing and bitstream revision,
  validate lengths and padding, and integrate with depacketization/FEC before advertising support.
  This client's pinned moonlight-common-c has neither PyroWave negotiation nor its framing path.
- **HDR presentation:** the host
  [conversion shader](https://github.com/RamazanKara/Butterpollo/blob/6772d4019c0837e372b801637f3bf1b57bea2765/rust/windows/src/shaders/color.hlsl)
  writes ten-bit codes normalized by 1023 into R16_UNORM planes. Limited-range luma is 64–940;
  chroma is centred at 512 with scale 896. A port needs full-resolution high-precision planes,
  correct BT.2020/PQ and range conversion, and a supported ten-bit HDR surface format/colour-space
  pair. It must also handle the existing HDR control metadata and HDR/SDR transitions. Android
  MediaCodec's `KEY_HDR_STATIC_INFO` does not configure a Vulkan swapchain.
  [Vulkan HDR metadata](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_hdr_metadata.html)
  supplies mastering/content metadata but does not select colour encoding; both paths need validation.

The [compatible protocol definitions](https://github.com/Nonary/moonlight-common-c/blob/d6a11bc685b41037b352a96f29d08276fe5359ba/src/Limelight.h)
use separate client and server namespaces:

| PyroWave profile | Client format | Server capability |
| --- | ---: | ---: |
| Eight-bit 4:2:0 | `0x010000` | `0x00800000` |
| Eight-bit 4:4:4 | `0x020000` | `0x01000000` |
| HDR10 4:2:0 | `0x040000` | `0x02000000` |
| HDR10 4:4:4 | `0x080000` | `0x04000000` |

`NvConnection.negotiateVideoFormats()` now excludes these unimplemented client codec families
before the native handshake. The synthetic Butterpollo serverinfo fixture advertises all four
alongside conventional HEVC/AV1 HDR (`0x07830301`). Tests verify AV1 HDR, HEVC HDR, SDR/H.264 fallback,
and that PyroWave HDR/4:4:4 bits do not create conventional HDR/4:4:4 support. Existing native RTSP
still chooses AV1, HEVC or H.264 from the ordinary host markers. No PyroWave setting or capability
is exposed, including on Vulkan-capable Android devices.

### Local measurement limit

No Android emulator or system image is installed in the supplied SDK. A small native Windows
capability probe directly loaded Chrome 154.0.8037.98's **software** `vk_swiftshader.dll`, with its
process restricted to two CPUs; it created a Vulkan 1.3 instance and queried the device, without
creating a decoder or submitting GPU work. DLL SHA-256:
`b08a7477b6e0f2ce9669cfbb8f13e5dd9ce6156d956e15641534489f80c0b40a`.

| Queried property | SwiftShader Device (Subzero) |
| --- | --- |
| Device API | Vulkan 1.3.0 |
| shaderInt16 / storageBuffer8BitAccess / shaderFloat16 | false / false / false |
| timelineSemaphore / synchronization2 | true / true |
| subgroupSizeControl / computeFullSubgroups | true / true |
| Subgroup operations / min–max size | `0xbf` / 4–4 |
| shaderStorageImageWriteWithoutFormat | true |

This fails the existing Android port's conservative feature gate, not a test of all possible
upstream decoder configurations or mobile hardware. There are **zero measured decoded frames**;
decode avg/p95/p99 are **unavailable**, not 0 ms. Milestone 1's `FrameLatencyStats` needs actual
decoder-input and completed-output timestamps. Feeding invented timestamps, timing a rejected
probe or copying the upstream 5.7 ms value into its CSV would not satisfy the requested benchmark.
No such benchmark result is claimed. This environment limitation does not establish mobile
infeasibility and is not a substitute for implementing the missing decoder.

Before enabling PyroWave, real-device testing must cover:

- Adreno and Mali/Immortalis: selected kernel's actual subgroup, storage/texel-buffer, image-format,
  queue and memory limits; unsupported devices must retain HEVC/AV1 negotiation. Validate initialization
  failure, surface replacement, pause/resume, rotation and HDR mode changes without leaking Vulkan objects.
- Ten-bit ramps and fine 4:4:4 colour text on an HDR panel against the host reference: limited/full
  range, BT.2020/PQ, black/white levels, no eight-bit intermediate, mastering/MaxCLL/MaxFALL metadata,
  missing/changed metadata and ten-bit SDR. Verify the actual SurfaceView swapchain and display output.
- Current Butterpollo container and record modes, bitstream mismatch, malformed/short/duplicate records,
  packet loss/reorder/FEC and reconnects. Repeat HEVC/AV1/H.264 fallback with stock Sunshine/Apollo hosts.
- Actual frame completions at 720p/1080p and native resolution, 60/120/240 Hz where supported, including
  sustained thermals. Record milestone 1 input-to-output avg/p95/p99 and per-frame CSV using completed
  GPU work; report GPU timestamp duration separately. Present submission is not scanout. Repeat on a
  capable software implementation for correctness, but do not infer phone timing from software or desktop GPU results.

## Verification and hardware follow-up

Passed for the milestone 4 exclusion/audit change: `testNonRootDebugUnitTest` (30 tests, no failures),
`assembleNonRootDebug` (ARM64), and `lintNonRootDebug` (zero errors, 194 warnings),
using JDK 17, Gradle `--no-daemon --max-workers=2` and
ndk-build `-j2`. Other ABIs and the root flavor were not built to keep shared-machine load low.
The temporary Gradle init script adds `-j2` to `android.defaultConfig.externalNativeBuild.ndkBuild.arguments`;
`-Pandroid.injected.build.abi=arm64-v8a` restricts this verification build to ARM64.

Local JUnit tests cover launch/resume query construction, fractional/high refresh, exact portrait pixels,
scale, unavailable virtual drivers, Sunshine/Apollo/GFE compatibility, app-list extension fixtures,
codec intersection, exclusion of all four PyroWave profiles, conventional HDR/4:4:4 fallback,
HDR metadata layout and host latency units/percentiles/CSV.
XML fixtures are synthetic protocol examples, not captured live sessions. JUnit and kXML are test-only
dependencies: assertions and a real XML pull parser without an emulator. Build with JDK 17 and the
installed SDK; cap Gradle workers and ndk-build at two. No host build, service, GPU work or live stream
is part of Android build verification; the separate software Vulkan capability query above is not a decode benchmark.

Real-device testing still required:

- Pair two installations, reconnect/resume and unpair; verify distinct host device/display identities,
  existing-pair upgrade behavior and stock Sunshine/Apollo compatibility.
- Request native portrait, landscape and fullscreen sizes at 60/90/120/144+ Hz and fractional 59.94/119.88;
  check selected Android mode, host geometry/rate, 100%/150% render scale, driver-unavailable fallback,
  device/app overrides and display restoration. Host rounds odd dimensions to even; AMD AV1 may pad
  coded dimensions, so inspect edge pixels and cropping on non-aligned native sizes.
- Exercise HEVC and AV1 HDR10 on real HDR panels: ten-bit output, BT.2020/PQ, mastering/content metadata,
  HDR on/off and changed/missing metadata, host ten-bit SDR override, SDR-display and codec-mismatch fallback.
  Check API 24–28 metadata clearing and that HDR stays on SurfaceView when TextureView was selected.
- Exercise actual AVC/HEVC 4:4:4 decoders (HEVC profile requires API 37), fine colored text, size/rate
  capability reporting and 4:2:0 fallback against Radeon and other hosts; verify HDR wins over SDR 4:4:4.
- Compare host processing overlay/CSV with host logs, including absent timing, drops, reconnects and
  sustained high FPS; client render callbacks are not physical scanout measurements.
