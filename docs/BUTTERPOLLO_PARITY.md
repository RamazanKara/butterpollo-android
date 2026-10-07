# Butterpollo Android parity — milestone 3

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
| HEVC/AV1 ten-bit 4:4:4, AV1 eight-bit 4:4:4 | Encoder-dependent standard flags; Radeon principally offers HDR 4:4:4 through PyroWave | No standard Android profile identifiers for these combinations in SDK 37; never inferred from Main10 or output color formats. Uses supported 4:2:0 instead. PyroWave deferred. |
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
| PyroWave | Codec/SDP flags, bitstream ID, adaptive FEC/records, `PyroWaveHostLinkMbps`, bandwidth probe bytes/endpoint | Deferred; no MediaCodec decoder or falsely advertised support. |
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

## Verification and hardware follow-up

Passed: `testNonRootDebugUnitTest` (26 tests), `assembleNonRootDebug` (ARM64),
and `lintNonRootDebug`, using JDK 17, Gradle `--no-daemon --max-workers=2` and
ndk-build `-j2`. Other ABIs and the root flavor were not built to keep shared-machine load low.

Local JUnit tests cover launch/resume query construction, fractional/high refresh, exact portrait pixels,
scale, unavailable virtual drivers, Sunshine/Apollo/GFE compatibility, app-list extension fixtures,
codec intersection, HDR/4:4:4 fallback, HDR metadata layout and host latency units/percentiles/CSV.
XML fixtures are synthetic protocol examples, not captured live sessions. JUnit and kXML are test-only
dependencies: assertions and a real XML pull parser without an emulator. Build with JDK 17 and the
installed SDK; cap Gradle workers and ndk-build at two. No host build, service, GPU work or live stream
is part of verification.

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
