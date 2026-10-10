# Rubylight host and Android features

[Documentation](index.md) · [Building](building.md)

How the Android client works with each Rubylight host feature.

| Capability | What Rubylight Android does |
| --- | --- |
| Discovery, manual addresses, IPv6, custom ports | Finds PCs on your network, saves hosts, accepts manual addresses and follows the ports the host advertises. |
| Wake-on-LAN | Wakes your PC with its validated MAC address over every interface, broadcast and resolved destination. |
| PIN and one-time-PIN pairing | Certificate pairing with a cancellable flow and a persistent installation identity. |
| Device permissions | Separate list, view, launch, input, clipboard and command permissions, set by the host. |
| Library and artwork | Shows your apps in host order and caches artwork until the host publishes new art. |
| Launch, resume, disconnect, quit | Checks permissions first and confirms before ending a game. |
| Remote Monitor / Input-only | View-only video and audio, or controls and feedback without starting decoders. |
| H.264 / HEVC / AV1 | Hardware decoding with automatic codec selection and per-device low-latency decoder tuning ([decoder notes](../decoder-errata.txt)). |
| HDR10 | Main10 negotiation, BT.2020/PQ, static display metadata and smooth HDR transitions. |
| YUV 4:4:4 | Full-chroma conventional profiles and PyroWave chroma modes for sharp colored text. |
| PyroWave | 64-bit Vulkan decoder with record framing, diagnostics and automatic codec fallback. |
| Resolution / fractional refresh / virtual display | Per-PC resolution and refresh rate, host render scale and a matching host virtual display. |
| VRR / Android adaptive refresh | Follows the game's cadence on adaptive displays and runs at maximum refresh elsewhere. |
| Pacing and decoder queues | Lowest latency, balanced, capped and smooth pacing with bounded output queues. |
| Client upscaling | Bilinear, FSR 1.0 and SGSR 1 with adjustable sharpening. |
| Automatic bitrate | Per-PC adaptation from loss, RTT variation and decode headroom, up to 500 Mbps at runtime. |
| Audio | Stereo, 5.1 and 7.1, with low-latency AAudio for stereo on Android 8.1 and later. |
| Controllers and mappings | Multiple players, per-model button mapping and built-in Xbox USB drivers. |
| DualSense / Edge | Adaptive triggers, rumble, lightbar and Edge paddles over the direct USB driver. |
| Touch, pen, mouse and keyboard | Trackpad, direct and multi-touch, native pen, pointer capture and full keyboard input. |
| Clipboard / server commands | Foreground text transfer in both directions and the host's configured commands. |
| Performance overlay / CSV | Compact and Advanced views with stage timings, host processing and CSV export. |
| Connection test / local benchmark | Connection and throughput test, plus a hostless timing benchmark. |
| Problem reports | Redacted local events, decoder details and the last crash record, shared when you choose. |
| PiP / shortcuts / frontends | Picture-in-picture, pinned app shortcuts and launcher entries for ES-DE, Daijisho and Pegasus. |
| Settings / accessibility | Five sections, search, presets, PC profiles, a control layout editor, TalkBack and D-pad navigation, in English and German. |
| Host administration | Devices, app overrides, display layout and host services are managed in the host web console. |

See [building](building.md) for reproducible checks.
