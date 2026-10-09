# Rubylight Android troubleshooting

Short fixes for the common problems. Host-side details live in the
[Rubylight host troubleshooting guide](https://github.com/RamazanKara/Rubylight/blob/main/docs/troubleshooting.md).

**Jump to:** [Add and pair a host](#add-and-pair-a-host) · [Firewall and ports](#firewall-and-ports) ·
[No video](#no-video-or-a-black-screen) · [Stutter](#stutter-or-dropped-frames) · [Latency](#lower-latency) ·
[HDR and PyroWave](#hdr-and-pyrowave-requirements) · [Support report](#what-to-include-in-a-report)

## Add and pair a host

1. Start Rubylight on the PC. The phone and the PC must be on the same local network; guest Wi-Fi
   and access-point isolation stop them from seeing each other.
2. Wait for the PC to appear in the app. If it does not, tap **Add PC** and enter its local IP address
   (for example `192.168.1.20`). On Windows, `ipconfig` shows it as the IPv4 address.
3. Tap the PC. The app shows a four-digit PIN. On the PC, open the console at
   **https://localhost:47990**, go to **Devices**, enter the PIN for the waiting request and select **Pair**.
4. If **Allow pairing** is off in the host's settings, pairing fails at once. Turn it on and try again.

**One-time PIN.** If the host has already created a one-time PIN and passphrase for this device
(Apollo shows this on its PIN page; Rubylight creates one through `POST /api/otp`), long-press the
PC and choose **Pairing → Pair with one-time PIN**. It expires three minutes after it was created.

If pairing works but apps do not appear or do not start, open **Devices → Edit** on the host and allow
**List apps**, **View streams** and **Launch apps** for this device. Missing input permissions give
video without touch, mouse or controller input.

## Firewall and ports

Rubylight's installer adds a Windows Firewall rule for the local network. If the app cannot reach
the host, allow `butterpollo.exe` on **Private** networks rather than turning the firewall off.

| Port | Protocol | Use |
| --- | --- | --- |
| 47984 | TCP | Paired HTTPS requests |
| 47989 | TCP | Discovery and pairing |
| 48010 | TCP | Stream setup (RTSP) |
| 47998, 47999, 48000 | UDP | Video, control, audio |
| 47990 | TCP | Web console (open on the host PC itself) |

These are the defaults. A changed base port on the host moves all of them by the same amount.
Streaming over the Internet needs those ports forwarded or a VPN; Rubylight is tuned for a LAN.

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
- Turn on **automatic bitrate** under **stream menu → Bitrate** for Rubylight hosts. It lowers the bitrate when
  packets are lost and raises it again slowly.
- Match the stream frame rate to the phone's screen (60, 90, 120 Hz). **This PC → Stream settings** accepts
  fractional rates such as 59.94 for Rubylight hosts.

## Lower latency

- Keep **Settings → Advanced → Frame pacing** on **Lowest latency** and leave **Android low-latency mode** and
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
| Queue wait | Time from enqueue to input: input buffers or the submission thread are backed up. Lower resolution/FPS; disable **Phone performance hints** if enabled. |
| Decode time | Time from input to output: try another codec (H.264, HEVC, AV1), then lower resolution/FPS or turn off HDR/4:4:4. This includes codec/driver scheduling, not only silicon execution. |
| Display wait, released/displayed FPS, estimated display drops | Time from output to rendered: use **Lowest latency** pacing and match FPS to the display. A display bottleneck is separate from hardware decode. |
| Decoder, low latency supported, decoder settings accepted/unconfirmed | Keep Android/chipset low-latency settings on. Standard low latency requires the advertised feature. Accepted means the value was echoed in the codec's [input format](https://developer.android.com/reference/android/media/MediaCodec#getInputFormat()); unconfirmed keys may be ignored. Compare another codec. |
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

**PyroWave** is experimental. It needs a Rubylight host, a phone with a capable Vulkan GPU, and a
fast wired network: about **280 Mbps at 720p/60** and **400 Mbps at 1080p/60**. 4K at 60 FPS needs
about 1600 Mbps, which this client cannot reach yet. Long-press a paired PC and choose
**This PC → Test your connection** to measure the link before switching. On slower networks use HEVC or AV1.

## What to include in a report

The report contains recent events, the last crash record, app version, Android API level and device
model. Addresses, user-assigned device and host names, PINs, credentials and exception messages are
omitted. Add what happened when sharing; nothing is sent until you choose an app.

Long-press the PC, choose **This PC → View details** and tap **Copy debug info**. Paste that into the report
together with the phone model, Android version, the codec/resolution/frame rate you used, what you
saw, and the time it happened. Add the host log from the console (**Troubleshooting → Logs**).

You can also use **Settings → App → Report a problem** to share a redacted report.

Report Android issues at
[github.com/RamazanKara/rubylight-android/issues](https://github.com/RamazanKara/rubylight-android/issues).

## Connection test results

The test sends ten host requests. Ping is their average round-trip time; jitter is the average
change between consecutive successful requests. Failed requests count HTTP failures, not lost UDP
video packets. The rate beside that percentage is the download throughput from a 32 MiB HTTP test
on supported paired hosts; the PC link line is the host network adapter's reported speed. These
measurements do not change stream settings or replace the live performance overlay. The test may
use mobile data and briefly slow other traffic. Cancel or leave the screen to stop it.

If speed is unavailable, pair with a host that supports the bandwidth test. If the test fails,
keep the PC awake, start the host, check its firewall and use the same network. For lower delay,
move closer to the router, connect the PC by Ethernet and pause downloads. The separate Internet
port check uses a public test server; its failure does not prove that local streaming is blocked.

## Wake-on-LAN and pairing recovery

Connect and pair while the PC is awake so Rubylight can save its network adapter's MAC address.
Enable Wake-on-LAN in both the PC firmware and adapter settings (often **Wake on Magic Packet**).
Keep the PC connected by Ethernet and test waking it from sleep on the same network. Wi-Fi,
shutdown and waking across routers depend on the PC and network. Long-press the offline PC and
choose **Wake PC**. If it stays asleep, check adapter power settings; sending a wake packet does
not confirm delivery.

For a rejected one-time PIN, create a fresh PIN and check its passphrase. Finish or cancel any old
pairing request in the host console before retrying. If the host refuses to pair during a game,
stop the host stream first. Sunshine and Apollo use their own pairing pages.

## Presets, PC profiles and reset

Presets set bitrate, frame rate, variable refresh, frame pacing and upscaling together:

| Preset | Bitrate | Frame rate | Codec | Pacing | Upscaling |
| --- | --- | --- | --- | --- | --- |
| Balanced | 15 Mbps | Screen maximum | Auto | Balanced, VRR off | Off |
| Low latency | 15 Mbps | Screen maximum | Keep selected codec | Lowest delay, VRR on | SGSR 1 |
| Best quality | 40 Mbps | Screen maximum | Auto | Balanced, VRR off | FSR 1.0 |
| Battery saver | 8 Mbps | 30 FPS | Auto | Frame-rate cap, VRR off | Bilinear |

Auto prefers low-latency AV1, then HEVC, then H.264. Screen maximum is capped at 1000 FPS;
VRR always requests the screen's maximum rate. Resolution and HDR stay as selected. Per-PC video
profiles override global video values and take effect on the next stream. Host display layout and
device/app policies still apply. Fractional refresh rates need Rubylight; other hosts round to
whole Hz. Native resolutions may need a host virtual display or a matching custom resolution.
Rubylight and Apollo can match resolution and frame rate automatically through their virtual
display; render scale at 100% matches the stream resolution.

Reset restores global stream, controls, overlay, audio, app and advanced preferences. It keeps
paired PCs, PC profiles, controller button mappings, the on-screen control layout and app language.

## Advanced video, input and bitrate options

- Client-side upscaling uses the GPU for lower-resolution SDR streams. FSR 1.0 uses EASU and RCAS
  sharpening; SGSR 1 uses one pass and is fastest on Snapdragon/Adreno. Off lets Android scale
  directly. HDR/10-bit and PyroWave keep direct output. Repeated extra latency or dropped frames
  switch back to direct output until reconnect. Upscaling uses more battery; Advanced stats show
  the current mode and timing. Sharpness applies from the next stream: 0% disables extra sharpening,
  100% is maximum, and SGSR keeps its spatial reconstruction at 0%.
- Standard low-latency decoding requires Android 11 or later; chipset options depend on the decoder.
  Turn them off if video freezes, becomes unstable or shows artifacts. Decoder performance hints
  require Android 6 or later and can increase power use and heat. Phone performance hints require
  Android 12 or later and default to off because wake-up latency can increase.
- Keeping only the newest frame needs Balanced pacing and may stutter on weak Wi-Fi. The compatibility
  video view adds latency and power use and is unavailable with HDR or PyroWave. Network priority
  changes streaming thread priority, may use more power and may be ignored by the device. Immediate
  mouse/pen input can increase network load with very fast mice.
- VRR overrides frame pacing: supported Android 16 screens use adaptive refresh, while other screens
  run at their maximum rate. Lower refresh rates can save power but add delay.
- YUV 4:4:4 needs compatible host and decoder support; otherwise it falls back to 4:2:0. HDR takes
  priority. Full-range video is experimental and may lose shadow or highlight detail. PyroWave also
  supports HDR when both ends support it. PyroWave needs Android 10+, a 64-bit app, Vulkan 1.3,
  suitable subgroup operations, GPU features, formats, limits, graphics queue and swapchain support;
  a generic unsupported-GPU message can indicate any of these checks failed.
  PyroWave uses Vulkan rather than MediaCodec, so Android codec low-latency keys do not apply to it.
- Allow USB access for DualSense during a stream to use adaptive triggers, rumble, the lightbar and
  Edge back paddles. Rubylight owns the pad until disconnection; denying access keeps Android's
  usual controller support. Reconnect the cable to retry. Bluetooth uses Android's controller support.
- Controller motion sensors use extra power and data. Device motion fallback can make the pad appear
  as a PlayStation controller. Desktop mouse acceleration can break mouse look in games. Native
  multitouch sends fingers directly to supported hosts and falls back to direct mouse input otherwise.
- Automatic bitrate is saved per PC and responds to network loss up to your selected bitrate and
  the host cap. PyroWave saves its choice separately and also considers queued frames and decode time,
  up to the selected bitrate or 500 Mbps. Manual session bitrate changes disable automatic bitrate;
  the host may cap the result. If the host rejects an update, enable automatic bitrate again in the menu.
- The host enforces device permissions and app-specific limits. Change permissions under **Devices**
  in its web console. Commands are defined on the host; a sent command has no reported execution result.
  Disconnecting leaves the host app running for later resumption. Host frame limits are informational
  on Android; the phone still uses its own frame pacing or VRR.
- The compact overlay shows displayed FPS, network-plus-decode latency and frame loss. Hold it to
  switch to the detailed view. Display drops are estimated when no render callback arrives within
  five seconds. Missing measurements stay blank in the latency CSV; a backlog can lose CSV rows.

## Game shortcuts for frontends

The ROM-folder export creates one file per game inside a `butterpollo` subfolder. ES-DE, Daijisho
and Pegasus launch streams through these files. Choosing the ES-DE folder also adds the Rubylight
system and game covers; skip this step for other frontends. Open the PC's game list first to refresh
the exported games, then restart ES-DE after export.
