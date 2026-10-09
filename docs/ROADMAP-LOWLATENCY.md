# Roadmap: the lowest-latency Android streaming client

Goal (owner, 2026-10-08): Rubylight Android supports PyroWave, is the best low-latency Android
game-streaming client available, and works with frontends such as ES-DE.

Target devices are 2024-model phones and newer (Snapdragon 8 Gen 3/Elite, Dimensity 9300+,
Tensor G4+, Exynos 2400+), Wi-Fi 6/6E/7 or USB Ethernet, and the Rubylight host on an RX 7900 XT.
Older phones keep working through the normal codecs; they are not tuned or tested.

Every latency claim below must come from a measurement on a real phone against the real host,
using the per-frame CSV and host processing numbers. Nothing ships on reasoning alone.

## Where we stand (2026-10-08)

| Area | State in this repo |
| --- | --- |
| PyroWave | Experimental, explicit codec choice. Vulkan 1.3 decoder ported from artemis-android-pyrowave (GPL-3.0), PyroWave `186f0393`, HDR10 and 4:4:4, warm-up on the real surface, fallback to AV1/HEVC/H.264. Ordinary packet transport only; no adaptive records/FEC; runtime bitrate capped at 500 Mbps by the host endpoint. Not yet run on a phone. |
| Decoder latency | `KEY_LOW_LATENCY`, Qualcomm/HiSilicon/MediaTek/Exynos vendor low-latency keys, `KEY_PRIORITY` realtime, operating rate, late-frame dropping, four frame-pacing modes. |
| Display | `setFrameRate` / preferred display mode matching, fractional refresh to 0.01 Hz, SurfaceView by default, `preferMinimalPostProcessing`. |
| Network | `WIFI_MODE_FULL_LOW_LATENCY` on API 29+, high-perf lock below, common-c FEC and QoS, opt-in loss-driven bitrate for normal codecs. |
| Input | Unbuffered dispatch, NVIDIA immediate input, controller remapping, native multi-touch. |
| Telemetry | Overlay and per-frame CSV: receive→decode avg/p95/p99, host processing avg/p95/p99, RTT. Decode→present (scanout) is not measured. |
| Frontends | New: `.art` entries (Artemis compatible), **Add games to ES-DE** export with ES-DE system files and covers, extras for Daijisho/Pegasus/`am start`. See [FRONTENDS.md](FRONTENDS.md). |

## How we compare

Checked against each project's own docs on 2026-10-08.

| | Moonlight Android | Artemis | Rubylight Android |
| --- | --- | --- | --- |
| H.264 / HEVC / AV1, HDR10 | Yes | Yes | Yes |
| PyroWave (wavelet intra-only codec, 200+ Mbps LAN) | No | Only in the unofficial artemis-android-pyrowave fork | Built in, explicit opt-in, HDR and 4:4:4 |
| Standard 4:4:4 | No | No | Where the decoder advertises it, with 4:2:0 fallback |
| Virtual display, native resolution, fractional refresh | No | Virtual display and custom resolution | Yes, per PC, plus render scale |
| Automatic bitrate | No | No | Opt-in for H.264/HEVC/AV1 |
| Host timing in overlay | No | No | Host processing avg/p95/p99 next to client latency |
| ES-DE / Daijisho | `am start` extras only | `.art` files plus export tool | `.art` files, built-in ES-DE export with covers, extras |
| Clipboard, server commands | No | Yes | Yes |
| Custom virtual buttons | No | Yes | Layout editing only |

Where we must still win: measured PyroWave on phones, decode→present latency, Wi-Fi tail latency
(p99), and an adaptive bitrate that also works for PyroWave.

## Plan

Ordered by expected latency gain per effort. "Cloud" items are UI, settings, docs and Java glue;
"Codex" items are native, codec or measurement work dispatched from the laptop.

### 1. Measure first (Codex + real phone)

1. **Present time.** Add decode→present to the CSV/overlay: `OnFrameRenderedListener` (render
   timestamps) for MediaCodec, `VK_GOOGLE_display_timing` or `VK_KHR_present_wait` where available
   for PyroWave. Report receive→present avg/p95/p99 as the headline "client latency".
2. **Baseline matrix** on the owner's phone: 1080p120 and native resolution, H.264/HEVC/AV1/PyroWave,
   each frame-pacing mode, Wi-Fi and USB Ethernet. Store CSVs under `docs/measurements/`.
3. **Glass-to-glass check** once: host flashes a frame on input, phone camera at 240 fps, to validate
   that the CSV numbers track what the eye sees.

### 2. PyroWave to "recommended" status (Codex, host + client)

1. **Phone certification**: run the explicit PyroWave path on Adreno 750/830 and Immortalis-G720/G925;
   fix whatever breaks first (swapchain formats, subgroup sizes, HDR surface pairs).
2. **Present mode**: prefer `MAILBOX` when the panel refresh exceeds the stream rate, `FIFO_LATEST_READY`
   (`VK_EXT_present_mode_fifo_latest_ready`) where supported, `FIFO` otherwise. Measure each.
3. **Adaptive records/FEC**: implement the host's `pyrowaveAdaptiveFec`/`pyrowaveFeatures` framing so
   partially lost frames recover per 64×64 block instead of waiting for the next intra frame.
4. **Bitrate**: raise the client ceiling to the host's quality policy (about 400 Mbps at 1080p60,
   1.6 Gbps at 4K60) for Ethernet, and add a PyroWave-specific rate controller driven by loss and
   queueing delay rather than the 20 % steps used for normal codecs.
5. **Capability check in the UI** (cloud, after 2.1): show "PyroWave ready / not supported and why"
   in Settings → Video and on the PC's details, from the native runtime check.
6. **Default policy**: once 2.1–2.4 measure better than AV1 at equal quality on Wi-Fi 6E/7 and
   Ethernet, offer PyroWave as the suggested codec when the bandwidth test passes; never on weak links.

### 3. Decoder and frame pacing (Codex)

1. **ADPF**: create a `PerformanceHintManager` session (API 31+) for the decode and render threads
   with a target of one frame interval; report actual durations each frame. Keeps big cores awake
   without `setSustainedPerformanceMode`.
2. **Output release timing**: release decoded buffers with `releaseOutputBuffer(index, presentTimeNs)`
   aligned to the next vsync from `Choreographer` in the low-latency mode, instead of immediately,
   to avoid a full extra frame of queueing on 120 Hz panels when the decoder finishes just after vsync.
3. **Decoder errata**: refresh `decoder-errata.txt` for 2024+ SoCs (AV1 on Snapdragon 8 Elite,
   Tensor G4, Dimensity 9300) with measured decode times and which low-latency keys actually help.
4. **Surface path**: keep SurfaceView; add `SurfaceControl` frame-rate hints with
   `CHANGE_FRAME_RATE_ALWAYS` for streams below the panel rate.

### 4. Network (Codex, host + client)

1. **Wi-Fi tail latency**: log per-second jitter and loss; add a "Wi-Fi check" after the bandwidth test
   that reports band, link speed, and p99 jitter, with one-line advice (5/6 GHz, distance, USB Ethernet).
2. **FEC tuning**: let the client ask for higher FEC percentage when loss is bursty and lower it on clean
   links (host setting today); measure p99 against bitrate cost.
3. **Packet pacing**: host-side pacing of large PyroWave frames to avoid Wi-Fi bursts (host repo).

### 5. Input path (Codex)

1. Timestamp input events at `MotionEvent.getEventTimeNanos()` and send them immediately on the
   control stream; measure input→host receive with host logs.
2. Use `Window.setPreferMinimalPostProcessing` and `GameManager` performance mode where available.
3. Gamepad polling: verify Bluetooth controllers at 1000 Hz report rate are not batched by the view.

### 6. Frontends (cloud, this round)

Done in this round: `.art` entries, ES-DE export, Daijisho/Pegasus/`am start` docs. Next:
- remember the picked folders and refresh the ES-DE export automatically when the game list changes;
- Android TV/handheld: return focus to ES-DE after the stream on devices where ES-DE is the home app;
- an "Export for Daijisho" platform JSON.

## Host-side changes (Rubylight)

- PyroWave adaptive record/FEC framing documented for clients, with a version attribute the client can
  negotiate safely (today any `pyrowaveAdaptiveFec` attribute switches framing).
- Runtime `/bitrate` cap above 500 Mbps for PyroWave on wired links.
- Frame pacing of PyroWave packets and an optional input-flash test pattern for glass-to-glass checks.
- Artwork tokens that change when a cover changes (today the numeric app ID is reused).
