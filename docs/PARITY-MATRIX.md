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

Client usability pass (2026-10-09): JDK 17 assemble, unit tests and lint passed with
`--no-daemon --max-workers=2`: 460 tests, no failures/errors/skips; lint 0 errors, 180 warnings.
No phone, host, adb or emulator was used. The 360/393 dp rendering checklist remains open.

## Priority features

| Host feature | Android status | Gap and next step |
| --- | --- | --- |
| PyroWave (8/10-bit, 4:2:0/4:4:4, `186f0393`) | **Partial: implemented, device checks pending.** Vulkan decoder, explicit codec choice, HDR/4:4:4, readiness/present mode, codec fallback, bandwidth test and 2 Gbps startup cap. `pyrowaveFeatures=1` selects adaptive records on compatible Rust hosts; surviving records decode and missing records reuse prior coefficients. Opt-in bitrate control uses loss, queue delay and completed decode time. The overlay separates missing records, queue delay, completed decode and last GPU decode. | Native framing/negotiation and Java rate-control tests cover loss, corruption, decode budgets and caps. Measure concealment and recovery on a phone: a lost datagram can affect several records or a split record. Unknown host versions use ordinary framing; incompatible bitstreams use conventional codecs. Runtime `/bitrate` remains capped at 500 Mbps. |
| Nonary's 1000 Hz VRR mode / Android ARR | **Done in code.** Opt-in host VRR plus measured-cadence votes: Android 16 checks ARR support for SurfaceView/TextureView; Android 15 SurfaceView requests seamless cadence changes. Older devices, Android 15 TextureView and Android 16 without ARR retain the fixed-rate path. | Policy tests cover API/surface/support gates, fractional cadence, missing samples and burst caps. Android 15 cannot publicly query ARR support; hints are not a scanout guarantee. Measure on hardware. |
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
| Discovery, manual add, IPv6, Wake-on-LAN | Done: all active IPv4 interface broadcasts, global broadcast and resolved host addresses; deduplicated destinations and three sends per destination | JUnit covers packet bytes, invalid MACs, alternate-port bounds, multiple subnets, partial failure and retry. Routers/NIC power policy and actual wake remain hardware checks. |
| PIN and one-time-PIN pairing, device identity | Done | — |
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
| Settings search | Done in code: title/description/category search over device-filtered settings; results open the original setting | Unit-tested Unicode, accent/case, multi-word and literal matching. Check search, Back, rotation and focus on emulator. |
| Material 3 / accessibility / large text | Client pass complete: remaining explicit dialogs/progress indicators, controller mapping and Help navigation; role descriptions, 48 dp form/OSC settings targets and growing role/status text | Every Activity layout and dialog audited. Native preference dialogs retain the matching platform palette/shape for persistence. No Robolectric/screenshot suite exists; 360/393 dp, large fonts and TalkBack checks are listed in BUTTERPOLLO_PARITY.md. |
| Frontends (ES-DE, Daijisho, Pegasus) | Done: `.art` files, ES-DE export | Smoke test: prompt 4. |

Codex prompts live in the project's shared folder
(`butterpollo-android/codex-prompts-lowlatency.md`); numbers above refer to that file.
