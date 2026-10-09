# Butterpollo host ↔ Android parity matrix

Host comparison baseline (2026-10-08): the host's `main` at
[Butterpollo `2cbb513`](https://github.com/RamazanKara/Butterpollo/tree/2cbb513) (rc.24 released,
rc.25 changes on `main`). The host was only read. Detailed evidence, protocol notes and the real-device
checklist stay in [BUTTERPOLLO_PARITY.md](BUTTERPOLLO_PARITY.md); this page is the short status.
Client gap follow-up on 2026-10-09 also inspected the read-only local host reference
[`118a0abb`](https://github.com/RamazanKara/Butterpollo/tree/118a0abb18480ff1da5564c57a2e00049ed67e47).

Status: **Done** = implemented in this app, **Partial** = works with a stated limit,
**Gap** = missing. Nothing here is certified on a real phone yet; see the checklist in
BUTTERPOLLO_PARITY.md.

First-device robustness pass (2026-10-09, `bp-jobD`): JDK 17 assembly, unit tests and lint passed
with `--no-daemon --max-workers=2`: **470 tests**, no failures/errors/skips; **lint 0 errors,
180 warnings**. All four Android ABIs are in the debug APK. The final run used a 1.5 GiB heap and
two active processors. No host, phone, controller, adb or emulator was used; rendering, runtime
lifecycle and real network/controller checks remain open in the device checklist.

## Priority features

| Host feature | Android status | Gap and next step |
| --- | --- | --- |
| PyroWave (8/10-bit, 4:2:0/4:4:4, `186f0393`) | **Partial: implemented, device checks pending.** Vulkan decoder, explicit codec choice, HDR/4:4:4, readiness/present mode, codec fallback, bandwidth test and 2 Gbps startup cap. `pyrowaveFeatures=1` selects adaptive records on compatible Rust hosts; surviving records decode and missing records reuse prior coefficients. Opt-in bitrate control uses loss, queue delay and completed decode time. The overlay separates missing records, queue delay, completed decode and last GPU decode. | Native framing/negotiation and Java rate-control tests cover loss, corruption, decode budgets and caps. Measure concealment and recovery on a phone: a lost datagram can affect several records or a split record. Unknown host versions use ordinary framing; incompatible bitstreams use conventional codecs. Runtime `/bitrate` remains capped at 500 Mbps. |
| Nonary's 1000 Hz VRR mode / Android ARR | **Done in code; device verification pending.** VRR requests the panel maximum (host limit 1000 FPS). Confirmed Android 16 ARR follows decoder-output cadence capped at panel maximum and releases immediately; without ARR, including Android 15, the display stays at maximum with FIXED_SOURCE Surface votes or a window/mode vote for TextureView. Vsync scheduling uses the physical mode, not the app render-rate override. Presets use maximum refresh except Battery saver (30 FPS); Low latency preserves the codec, and Auto admits low-latency hardware AV1 ahead of HEVC/H.264. Overlay/CSV distinguish received, released and callback-observed shown FPS. | JDK 17 assemble, 479 unit tests and lint pass with two workers. Tests cover cadence recovery, fixed maximum fallback, a 60 Hz render override on a 120 Hz panel, presets/codecs, batched callbacks and half-rate presentation. CSV appends a timestamped two-second FPS sample and cumulative `present_drops`: an estimate of releases still unobserved after 5 s, excluding pre-release drops and resets. Callbacks can be delayed or missing on older Android, so this is not a scanout guarantee. Caller must run emulator smoke, stream AV1/HEVC at 120 FPS with VRR on, check shown ≈ received on ARR and fixed-rate fallback, and save `docs/screenshots/vrr-120.png`. No adb/emulator/phone or live host was used in this runner. |
| HDR10 (HEVC Main10, AV1 Main10) | **Done.** Negotiation, BT.2020/PQ, static metadata (`0x010e`) to `KEY_HDR_STATIC_INFO`, HDR↔SDR switches, host 10-bit SDR. | Real HDR10 panel check only. HDR10+ dynamic metadata is not sent by the host. |
| 4:4:4 | **Partial.** Standard AVC High 4:4:4 / HEVC Main 4:4:4 where the decoder advertises it; PyroWave 4:4:4 including 10-bit. | AMD encoders make 4:2:0 only, so on a Radeon host 4:4:4 means PyroWave. Android has no profile IDs for HEVC/AV1 10-bit 4:4:4; nothing to add. |
| AV1 / HEVC / H.264 | **Done.** Hardware MediaCodec, low-latency and vendor keys. [2024+ errata evidence review](../decoder-errata.txt) records 9300, S24+ Exynos and 8 Gen 3 reports and the limits of Elite/Tensor G4 evidence. | Research only: no unverified SoC tuning, blacklists or timing claims added. Measure the owner's exact phone, OS and decoder. |
| Stereo, 5.1, 7.1 audio | **Done.** Settings → Audio offers 7.1; Opus multistream, host channel map. | None known. |
| DualSense / DualShock 4: rumble, gyro, touchpad, lightbar, battery | **Done** on Android 12+ through `InputDevice` (dual-motor rumble, sensors, lights, battery). PS controllers report their type, so rc.23+ hosts give them a VHF DualSense. | Rumble strength and motion rate need a real controller check. |
| DualSense adaptive triggers (rc.23+, VHF driver) | **Done in code.** Separate off-by-default **USB DualSense driver** setting, Material 3 permission explanation and Android USB permission flow; HID input, rumble/lightbar and opaque host trigger effects. | Captured USB input and synthetic report/effect tests; real pad/firmware checks required. Bluetooth adaptive triggers and USB audio haptics are outside this driver. |
| Back paddles (Xbox Elite, DualSense Edge, rc.24 back-grip mapping) | **Done in code.** Xbox Elite evdev paddles; DualSense Edge USB left/right paddles, independent of Fn buttons. | Verify each Edge paddle on rc.24+; Edge tests use synthetic reports, not hardware captures. |
| Per-device virtual display, client resolution and refresh | **Done.** `virtualDisplay=1`, native/custom size per PC, fractional refresh to 0.01 Hz, render scale 50–200%. | Layout, DPI and sharing policy are host settings by design. |

## Everything else the host offers

| Host feature | Android status | Gap and next step |
| --- | --- | --- |
| Discovery, manual add, IPv6, Wake-on-LAN | Done: discovery empty-state actions and in-app wake setup help; all active IPv4 interface broadcasts, global broadcast and resolved host addresses; deduplicated destinations and three sends per destination | JUnit covers packet bytes, invalid MACs, alternate-port bounds, multiple subnets, partial failure and retry. Routers/NIC power policy and actual wake remain hardware checks. |
| PIN and one-time-PIN pairing, device identity | Done: cancellable pairing, fresh-PIN retry and plain-language network/PIN fixes | Check wrong/expired PINs, cancellation, backgrounding and retry on hardware. |
| Device permissions (view/launch/input/clipboard/commands) | Done: shown and refreshed before actions | — |
| App list, UUIDs, order, artwork versions | Done | Host reuses the numeric ID as artwork token (host side). |
| Launch, resume, quit, reconnect without quitting | Done | — |
| Remote Monitor and Input-only tiles | Done in code: accessible role/action labels; expanding role badges; monitor is view-only; Input-only skips media decoders/watchdogs; scoped disconnect and explicit Material 3 confirmation for replacement/termination | Role/confirmation unit tests cover cancellation, interruption, expiry, denial and unknown 410s. Verify ownership/rejoin with two paired clients. Host 410 is an XML status inside an HTTP 200 response. |
| Runtime bitrate and automatic bitrate | Done in code for Butterpollo, including decode-budget/headroom checks for ordinary codecs and a separate opt-in PyroWave controller (loss, queueing and decode time). Menus/host actions reset recovery streaks | Verify sustained congestion/recovery on a phone; the host runtime cap is 500 Mbps. |
| Clipboard text and server commands | Done (manual, permission-checked) | — |
| Frame limiter state | Shown in the stream menu | — |
| Host processing time | Overlay and CSV avg/p95/p99 | Decode→present timing: prompt 1. |
| Keyboard, mouse, touch, pen, native multi-touch | Done; numpad Enter/separator, Print Screen and supplementary Unicode fixes; Material touch-mode dialogs | Key translation/Unicode regressions covered by JUnit. Physical layouts, AltGr, mice and gesture cancellation still need device checks. |
| Multiple controllers, button remapping | Done (16 slots, per-model digital remap for Android and USB drivers, including DualSense/Edge) | Axis remap not planned. |
| Steam Deck dual trackpads and grips | Not applicable on Android | — |
| HDR on the host's virtual display (`VirtualDisplayHDRCapable`) | Host always reports true; HDR request is enough | — |
| Library sync, Lossless Scaling, RTSS, Playnite, updates, logs | Host-side; work with ordinary launches | Admin stays in the host's web console. |
| PiP controls | Done in code: local Disconnect action, session-scoped immutable intent and bounded portrait/ultrawide ratios | Unit-tested ratio bounds and active media-role action gating. Check system controls/lifecycle on Android. |
| Activity/Service lifecycle | Source audit complete; targeted fixes for pending bindings, stopped discovery/polling, queued dialogs, process restoration, PiP, controller detach and stream callbacks | All device scenarios remain open; this is not a crash/leak certification. See the lifecycle checklist. |
| Report a problem | Done: Settings → Support shares a bounded, redacted text event log through an unexported FileProvider | Current process only; fixed event text, app version and API level. No addresses, names, PINs, raw host replies or free-text exception details. Verify share targets on the phone. |
| Test your connection | Done: long-press any PC for latency, jitter, probe loss and optional existing host bandwidth download | Ten timed server-info requests (2 s deadline each); loss means failed HTTP probes, not UDP packet loss. Throughput requires a paired supporting host. |
| Streaming presets and help | Done: Balanced, Low latency, Best quality and Battery saver; grouped settings retain help alongside current values | Preset values and writes are unit-tested. Existing per-PC video profiles still take priority; measure power, heat and responsiveness on hardware. |
| Performance overlay | Done in code: Compact default (including legacy saved defaults), one line of shown FPS, network RTT + completed decode latency and frame loss with a health dot; unavailable values hidden for conventional and PyroWave streams. Advanced retains F1/F2 diagnostics under Video, Network, Decode and Host. English/German mode labels, persistent menu/long-press switching, full Advanced Copy stats, existing keyboard toggle and PiP hiding. | JDK 17 assemble, 490 unit tests and lint pass with two workers. Eight overlay tests cover missing/non-finite values, rounding, German numbers, PyroWave presentation timing and health thresholds. Debug preview/smoke script covers both modes at 393 dp in portrait/landscape. Caller must run the emulator smoke pass, save `docs/screenshots/overlay-compact.png` and `overlay-advanced.png`, send them to the Opus xhigh screenshot judge before test.5, and verify live values, clipboard, persistence, shortcut and PiP on a phone. No emulator, screenshots or live host were verified in this runner. |
| Settings search | Done in code: title/description/category search over device-filtered settings; results open the original setting | Unit-tested Unicode, accent/case, multi-word and literal matching. Check search, Back, rotation and focus on emulator. |
| Material 3 / accessibility / large text | Client pass complete: remaining explicit dialogs/progress indicators, controller mapping and Help navigation; role descriptions, 48 dp form/OSC settings targets and growing role/status text | Every Activity layout and dialog audited. Native preference dialogs retain the matching platform palette/shape for persistence. No Robolectric/screenshot suite exists; 360/393 dp, large fonts and TalkBack checks are listed in BUTTERPOLLO_PARITY.md. |
| Frontends (ES-DE, Daijisho, Pegasus) | Done: `.art` files, ES-DE export | Smoke test: prompt 4. |

Codex prompts live in the project's shared folder
(`butterpollo-android/codex-prompts-lowlatency.md`); numbers above refer to that file.
