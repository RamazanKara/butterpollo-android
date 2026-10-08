# Butterpollo host ↔ Android parity matrix

Checked on 2026-10-08 against the host's `main` at
[Butterpollo `2cbb513`](https://github.com/RamazanKara/Butterpollo/tree/2cbb513) (rc.24 released,
rc.25 changes on `main`). The host was only read. Detailed evidence, protocol notes and the real-device
checklist stay in [BUTTERPOLLO_PARITY.md](BUTTERPOLLO_PARITY.md); this page is the short status.

Status: **Done** = implemented in this app, **Partial** = works with a stated limit,
**Gap** = missing. Nothing here is certified on a real phone yet; see the checklist in
BUTTERPOLLO_PARITY.md.

## Priority features

| Host feature | Android status | Gap and next step |
| --- | --- | --- |
| PyroWave (8/10-bit, 4:2:0/4:4:4, `186f0393`) | **Partial.** Vulkan 1.3 decoder, explicit codec choice, HDR10 and 4:4:4 on SurfaceView, fallback to AV1/HEVC/H.264, bandwidth test. Per-PC bitrate now goes up to 2 Gbps, the host's setup limit. | Record framing (`pyrowaveAdaptiveFec`), so lost packets cost one block instead of the whole frame: Codex prompt 5. PyroWave-specific rate control: prompt 5. Readiness line in Settings and present mode: prompt 2. Runtime `/bitrate` stays capped at 500 Mbps by the host. |
| Nonary's 1000 Hz VRR mode | **Done (new).** Settings → Host display → **Variable refresh (VRR)** sends `vrr=1`; the host turns on its VRR pacing (and its 1000 Hz virtual display when its refresh policy allows). The phone then uses minimum-latency pacing at its highest refresh rate. | Android has no true VRR scanout for apps before Android 16's adaptive refresh rate (ARR). Use ARR where present and measure: Codex prompt 7. |
| HDR10 (HEVC Main10, AV1 Main10) | **Done.** Negotiation, BT.2020/PQ, static metadata (`0x010e`) to `KEY_HDR_STATIC_INFO`, HDR↔SDR switches, host 10-bit SDR. | Real HDR10 panel check only. HDR10+ dynamic metadata is not sent by the host. |
| 4:4:4 | **Partial.** Standard AVC High 4:4:4 / HEVC Main 4:4:4 where the decoder advertises it; PyroWave 4:4:4 including 10-bit. | AMD encoders make 4:2:0 only, so on a Radeon host 4:4:4 means PyroWave. Android has no profile IDs for HEVC/AV1 10-bit 4:4:4; nothing to add. |
| AV1 / HEVC / H.264 | **Done.** Hardware MediaCodec, low-latency and vendor keys, decoder errata. | Errata refresh for 2024+ SoCs: prompt 3 follow-up. |
| Stereo, 5.1, 7.1 audio | **Done.** Settings → Audio offers 7.1; Opus multistream, host channel map. | None known. |
| DualSense / DualShock 4: rumble, gyro, touchpad, lightbar, battery | **Done** on Android 12+ through `InputDevice` (dual-motor rumble, sensors, lights, battery). PS controllers report their type, so rc.23+ hosts give them a VHF DualSense. | Rumble strength and motion rate need a real controller check. |
| DualSense adaptive triggers (rc.23+, VHF driver) | **Gap.** The host sends trigger effects; this app does not receive them, and Android has no trigger API. | A USB DualSense driver that claims the pad and writes output reports: Codex prompt 6. Bluetooth needs root, so it stays out. |
| Back paddles (Xbox Elite, DualSense Edge, rc.24 back-grip mapping) | **Partial.** Xbox Elite paddles via evdev scan codes. | DualSense Edge paddles through the USB driver: prompt 6. |
| Per-device virtual display, client resolution and refresh | **Done.** `virtualDisplay=1`, native/custom size per PC, fractional refresh to 0.01 Hz, render scale 50–200%. | Layout, DPI and sharing policy are host settings by design. |

## Everything else the host offers

| Host feature | Android status | Gap and next step |
| --- | --- | --- |
| Discovery, manual add, IPv6, Wake-on-LAN | Done | — |
| PIN and one-time-PIN pairing, device identity | Done | — |
| Device permissions (view/launch/input/clipboard/commands) | Done: shown and refreshed before actions | — |
| App list, UUIDs, order, artwork versions | Done | Host reuses the numeric ID as artwork token (host side). |
| Launch, resume, quit, reconnect without quitting | Done | — |
| Remote Monitor and Input-only tiles | Partial: listed and launchable | Role-aware lifecycle and confirmation (HTTP 410) UI: cloud follow-up. |
| Runtime bitrate and automatic bitrate | Done for H.264/HEVC/AV1 on Butterpollo | PyroWave controller: prompt 5. |
| Clipboard text and server commands | Done (manual, permission-checked) | — |
| Frame limiter state | Shown in the stream menu | — |
| Host processing time | Overlay and CSV avg/p95/p99 | Decode→present timing: prompt 1. |
| Keyboard, mouse, touch, pen, native multi-touch | Done | — |
| Multiple controllers, button remapping | Done (16 slots, per-model digital remap) | Axis remap not planned. |
| Steam Deck dual trackpads and grips | Not applicable on Android | — |
| HDR on the host's virtual display (`VirtualDisplayHDRCapable`) | Host always reports true; HDR request is enough | — |
| Library sync, Lossless Scaling, RTSS, Playnite, updates, logs | Host-side; work with ordinary launches | Admin stays in the host's web console. |
| Frontends (ES-DE, Daijisho, Pegasus) | Done: `.art` files, ES-DE export | Smoke test: prompt 4. |

Codex prompts live in the project's shared folder
(`butterpollo-android/codex-prompts-lowlatency.md`); numbers above refer to that file.
