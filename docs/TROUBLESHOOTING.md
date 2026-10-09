# Butterpollo Android troubleshooting

Short fixes for the common problems. Host-side details live in the
[Butterpollo host troubleshooting guide](https://github.com/RamazanKara/Butterpollo/blob/main/docs/troubleshooting.md).

**Jump to:** [Add and pair a host](#add-and-pair-a-host) · [Firewall and ports](#firewall-and-ports) ·
[No video](#no-video-or-a-black-screen) · [Stutter](#stutter-or-dropped-frames) · [Latency](#lower-latency) ·
[HDR and PyroWave](#hdr-and-pyrowave-requirements) · [Support report](#what-to-include-in-a-report)

## Add and pair a host

1. Start Butterpollo on the PC. The phone and the PC must be on the same local network; guest Wi-Fi
   and access-point isolation stop them from seeing each other.
2. Wait for the PC to appear in the app. If it does not, tap **Add PC** and enter its local IP address
   (for example `192.168.1.20`). On Windows, `ipconfig` shows it as the IPv4 address.
3. Tap the PC. The app shows a four-digit PIN. On the PC, open the console at
   **https://localhost:47990**, go to **Devices**, enter the PIN for the waiting request and select **Pair**.
4. If **Allow pairing** is off in the host's settings, pairing fails at once. Turn it on and try again.

**One-time PIN.** If the host has already created a one-time PIN and passphrase for this device
(Apollo shows this on its PIN page; Butterpollo creates one through `POST /api/otp`), long-press the
PC and choose **Pairing → Pair with one-time PIN**. It expires three minutes after it was created.

If pairing works but apps do not appear or do not start, open **Devices → Edit** on the host and allow
**List apps**, **View streams** and **Launch apps** for this device. Missing input permissions give
video without touch, mouse or controller input.

## Firewall and ports

Butterpollo's installer adds a Windows Firewall rule for the local network. If the app cannot reach
the host, allow `butterpollo.exe` on **Private** networks rather than turning the firewall off.

| Port | Protocol | Use |
| --- | --- | --- |
| 47984 | TCP | Paired HTTPS requests |
| 47989 | TCP | Discovery and pairing |
| 48010 | TCP | Stream setup (RTSP) |
| 47998, 47999, 48000 | UDP | Video, control, audio |
| 47990 | TCP | Web console (open on the host PC itself) |

These are the defaults. A changed base port on the host moves all of them by the same amount.
Streaming over the Internet needs those ports forwarded or a VPN; Butterpollo is tuned for a LAN.

## No video or a black screen

- Check the host first: **Overview → Host readiness** must show a working encoder and screen capture.
  A locked PC, a sleeping monitor or a disconnected virtual display gives a black picture.
- In the app, set **Settings → Stream → Video codec** to **Automatic** and turn off HDR, then try again at
  1280×720, 60 FPS.
- If the picture is black, flickers or sits in the wrong place, turn on **Settings → Advanced → Compatibility video view**.
  It does not apply to HDR or PyroWave.
- If the stream ends with a decoder error, choose another codec. After three decoder crashes in a row
  the app resets the streaming settings itself.

## Stutter or dropped frames

- Turn on the performance overlay (**stream menu → Overlay → Show performance overlay**, or
  **Ctrl+Alt+Shift+S**). Network loss points at Wi-Fi or bitrate; high decode time points at the
  phone's decoder.
- Lower the bitrate first. Most LAN streams look good at 20–80 Mbps; on Wi-Fi start at 20–40 Mbps.
- Use 5 GHz or 6 GHz Wi-Fi close to the access point, or a wired USB Ethernet adapter.
- Turn on **automatic bitrate** under **stream menu → Bitrate** for Butterpollo hosts. It lowers the bitrate when
  packets are lost and raises it again slowly.
- Match the stream frame rate to the phone's screen (60, 90, 120 Hz). **This PC → Streaming settings for this PC** accepts
  fractional rates such as 59.94 for Butterpollo hosts.

## Lower latency

- Keep **Settings → Advanced → Frame pacing** on **Prefer lowest latency** and leave **Android low-latency mode** and
  **Chipset low-latency mode** on.
- Use a frame rate your screen can show; higher stream rates lower latency only when the display keeps up.
- A wired or uncongested 5/6 GHz connection matters more than any setting.
- Compare client latency with the host processing time on the overlay. Export the per-frame CSV from
  **Settings → Advanced → Export latency CSV** after a session for a closer look.

## Why is decode time high?

Choose **stream menu → Overlay → Advanced** and use **Copy stats**. **Decode time** now
measures `queueInputBuffer` to observed decoder output, excluding the native queue and display wait.
Every observed output counts, including frames dropped before presentation. The percentile lines
use their own valid samples from the last 600 frames; missing render callbacks show no data.

| Overlay line | What to try |
| --- | --- |
| Queue wait (enqueue → input) | Input buffers or the submission thread are backed up. Lower resolution/FPS; disable **Phone performance hints** if enabled. |
| Decode time (input → output) | Try another codec (H.264, HEVC, AV1), then lower resolution/FPS or turn off HDR/4:4:4. This includes codec/driver scheduling, not only silicon execution. |
| Present wait (output → rendered), released/shown FPS, present drops | Use **Prefer lowest latency** pacing and match FPS to the display. A display bottleneck is separate from hardware decode. |
| Decoder, FEATURE_LowLatency, low-latency keys | Keep Android/chipset low-latency settings on. Standard low latency requires the advertised feature. Accepted means the value was echoed in the codec's [input format](https://developer.android.com/reference/android/media/MediaCodec#getInputFormat()); unconfirmed keys may be ignored. Compare another codec. |
| Phone performance hints (ADPF) | Defaults and presets leave this off: short renderer CPU bursts against a full-frame target may reduce clocks. Existing saved choices are kept; compare on/off on your phone. |
| Network latency/variation, network frame loss | Improve Wi-Fi (nearby 5/6 GHz access point), wire the PC and lower bitrate. These are not hardware decode measurements. |
| Client latency, host processing | Use the split above to locate client delay; high host processing calls for lower game/encoder load on the PC. |

CSV columns `queue_wait_ms`, `input_to_output_ms` and `output_to_render_ms` correspond to the three
stages. `enqueue_ns` is in the same monotonic clock as input/output; `receive_to_input_ms` additionally
includes packet assembly before enqueue. **Report a problem** includes the last decoder configuration
even with the overlay off. ADPF defaults are conservative; device A/B testing is still needed.

## HDR and PyroWave requirements

**HDR** needs an HDR10 screen on the phone, Android 7 or later, a hardware HEVC Main10 or AV1 10-bit
decoder, and HDR set up on the host's streamed display. Without all of these the stream falls back
to SDR.

**PyroWave** is experimental. It needs a Butterpollo host, a phone with a capable Vulkan GPU, and a
fast wired network: about **280 Mbps at 720p/60** and **400 Mbps at 1080p/60**. 4K at 60 FPS needs
about 1600 Mbps, which this client cannot reach yet. Long-press a paired PC and choose
**This PC → Test your connection** to measure the link before switching. On slower networks use HEVC or AV1.

## What to include in a report

Long-press the PC, choose **This PC → View details** and tap **Copy debug info**. Paste that into the report
together with the phone model, Android version, the codec/resolution/frame rate you used, what you
saw, and the time it happened. Add the host log from the console (**Troubleshooting → Logs**).

You can also use **Settings → App → Report a problem** to share a redacted report.

Report Android issues at
[github.com/RamazanKara/butterpollo-android/issues](https://github.com/RamazanKara/butterpollo-android/issues).
