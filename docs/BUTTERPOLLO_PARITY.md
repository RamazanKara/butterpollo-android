# Butterpollo Android parity — milestones 3–4

Audited on 2026-10-07 against a shallow, unmodified checkout of
[Butterpollo `f144a731b1e82a790b918600ffdc73aed7273080`](https://github.com/RamazanKara/Butterpollo/tree/f144a731b1e82a790b918600ffdc73aed7273080).
The current Rust host is authoritative; the retained C++ implementation is historical.
“Supported” below means implemented protocol paths, not a real-device streaming certification.

| Capability | Host support | Client status |
| --- | --- | --- |
| Discovery, addresses, ports, wake | mDNS, IPv4/IPv6, `hostname`, `LocalIP`, `mac`, `HttpsPort`, `ExternalPort` | Existing discovery, manual addresses, port handling and Wake-on-LAN. |
| Pairing and device identity | PIN/certificate pairing, `PairStatus`, host `uniqueid`; device UUID bound to certificate | Existing pairing and certificate pinning. Now sends the persisted installation ID instead of a shared constant, the Android model as device name, and authenticated HTTPS unpair when paired. |
| One-time PIN, device permissions | OTP via admin API; `Permission`, per-device enable and access masks | Ordinary PIN pairing supported; host enforces permissions. OTP entry and permission-aware UI are deferred Apollo extras. |
| App list and artwork | `/applist`, `/appasset`; `AppTitle`, `ID`, `IsHdrSupported`, `UUID`, `IDX`, `ArtVersion`, permission-filtered entries | Existing ID-based launch, HDR hint and cover download; fixed parsing of whitespace/text outside app entries. UUID launch, host ordering and artwork version invalidation are deferred Apollo extras. Unknown fields remain compatible. |
| Launch, resume, quit and ownership | `/launch`, `/resume`, `/cancel`, `currentgame`, `currentgameuuid`, `state`, `gamesession`, `sessionUrl0` | Existing session operations and host errors; new display parameters apply to launch and resume alike. UUID ownership UI deferred. |
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
| HEVC/AV1 ten-bit 4:4:4, AV1 eight-bit 4:4:4 | Encoder-dependent standard flags; Radeon principally offers HDR 4:4:4 through PyroWave | No standard Android profile identifiers for these combinations in SDK 37; never inferred from Main10 or output color formats. Uses supported 4:2:0 instead. PyroWave disabled; see milestone 4 findings below. |
| Host frame timing | Video short header carries unsigned processing duration in 100 µs units; zero means unavailable | Now beside client latency as avg/p95/p99 over 600 observed frames, also `host_processing_ms` in per-frame CSV. Host claim-to-packet duration is not encode-only or end-to-end latency. No clock subtraction. |
| Capture/encode/frame-age breakdown | Detailed timing in host diagnostics/web API; not separately carried in standard video packets | Not fabricated from processing duration; admin telemetry UI deferred. |
| Audio | Opus stereo, 5.1, 7.1, quality/channel-map negotiation, local playback, encryption | Existing audio negotiation/playback. Endpoint selection remains host configured. |
| Transport and recovery | RTSP/encrypted RTSP, UDP video/audio, reliable encrypted control, FEC, keyframe recovery, optional reference invalidation, ping/connect-data extensions, QoS | Existing pinned moonlight-common-c `874ac954` handles these. Slice/reference negotiation and milestone 2 latency controls retained. |
| Keyboard/mouse/touch/pen | Standard input plus `x-ss-general.featureFlags` pen/touch bit | Existing keyboard, mouse, stylus and protocol capability fallback. Native finger-touch dispatch remains disabled upstream in favor of touch-as-mouse; deferred input UX. |
| Controllers and feedback | Multiple pads, touch bit, motion, battery, rumble/triggers, LED; driver-dependent DualSense effects | Existing controller arrival/touch/motion/battery and rumble/trigger/LED paths. Host exposes one touchpad; DualSense adaptive effects/secondary-pad extensions are not exposed by this client's core and are deferred. |
| Frame limiter and VRR | `FrameLimiterSupported`, `FrameLimiterEnabled`, `VirtualDisplayFrameLimiterEnabled`, `FrameLimiterFpsLimitMilliHz`; `vrr` aliases and SDP `vrrLowLatency`, 1000 Hz virtual mode | Host defaults apply. Client controls and Nonary VRR mode deferred with Apollo/Vibepollo extras. High-refresh fixed-rate requests do not claim VRR. |
| Runtime bitrate / ABR | `/bitrate`, `/api/abr/capabilities`: client-driven runtime bitrate, `supported:false` for host ABR | Existing startup bitrate only; runtime adjustment/ABR controller deferred Apollo extra. |
| Remote monitor/input and control tiles | Synthetic app IDs/UUIDs, `remote_monitor`, `input_only`, resume/disconnect/terminate/replace actions | Tiles can be listed by existing app parser; role-specific lifecycle/confirmation UI deferred. Not claimed as full remote-session support. |
| Clipboard and server commands | Permission-scoped text `/actions/clipboard`, advertised `ServerCommand` names | Deferred Apollo extras. |
| PyroWave | Codec/SDP flags, bitstream ID, adaptive FEC/records, `PyroWaveHostLinkMbps`, bandwidth probe bytes/endpoint | Disabled. Negotiation explicitly excludes unimplemented codec families and retains HEVC/AV1/H.264. No Vulkan decoder or PyroWave HDR presentation is shipped. Milestone 4 is incomplete; mobile infeasibility has **not** been established. |
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

## Milestone 4: PyroWave feasibility and disabled status

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
