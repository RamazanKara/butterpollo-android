# RUBYLIGHT demo shotlist

Offline fixture tour: **67.4 seconds at 30 fps**, including a 2.5-second intro,
66 seconds of UI, a 4.5-second outro and fourteen 0.4-second crossfades.
Capture adds one second before and after each shot; the renderer trims those
handles. The caller runs the capture scripts on a 1080x2400 portrait device.

Intro: **RUBYLIGHT** / **Your games. Your screen. Your stream.**

Outro: **RUBYLIGHT** / **Start your stream.** / **Get RUBYLIGHT** /
**https://github.com/RamazanKara/rubylight-android/releases**

This table is the scripts' manifest. Keep the column order and IDs. The last
column is the demo state; `upscaling` navigates through the real settings UI.
`~` denotes a caption line break. Screenshot paths are relative to
`docs/screenshots`. Every still now comes from the populated debug demo.

| ID | Seconds | Exact caption | Heading | Feature callout | Storyboard screenshot | Crop | Driver |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| 01-launch | 5 | Your PC games. In your hands. | Start~here. | RUBYLIGHT~Offline demo tour | demo/pc-list.png | full | hosts |
| 02-discovery | 5 | Your PCs, together. | Find~your PC. | Online and offline~Fixture computers | demo/pc-list.png | full | hosts |
| 03-pairing | 5 | Paired and ready to play. | Make~the connection. | Living-room PC~Paired demo host | demo/pc-list.png | full | hosts |
| 04-library | 5 | Your library, ready to play. | Pick~your game. | Ten demo titles~Original cover art | demo/library.png | full | library |
| 05-stream | 5 | Tap a game. Start your stream. | Press~play. | Local sample~Synthetic gameplay | demo/stream.png | full | stream |
| 06-compact | 5 | Keep the essentials in view. | Stay~in the game. | Compact overlay~Scripted demo readings | demo/stream-compact.png | full | compact |
| 07-advanced | 5 | See every stage of your stream. | See~the detail. | Advanced overlay~Scripted demo readings | demo/stream-advanced.png | full | advanced |
| 08-presets | 5 | Pick the setup that fits. | Set~your pace. | Stream presets~Isolated demo settings | demo/settings-presets.png | full | settings |
| 09-upscaling | 5 | Choose how your stream scales. | Make~it clear. | Upscaling options~Bilinear / FSR 1 / SGSR 1 | demo/upscaling-options.png | full | upscaling |
| 10-controller | 5 | Play with your controller. | Take~control. | Controller buttons~Demo input mappings | demo/controller.png | full | controller |
| 11-touch | 5 | Make touch controls your own. | Make~it yours. | On-screen controls~Real controller overlay | demo/touch-controller.png | full | touch |
| 12-pip | 5 | Keep your stream in view. | Keep~it in view. | Picture in picture~Looping local sample | demo/pip.png | full | pip |
| 13-performance | 6 | Explore FPS, latency and frame loss. | Know~your stream. | Performance overlay~Illustrative values | demo/stream-advanced.png | full | advanced |

Capture direction:

1. Hold the populated PC list for shots 01–03; the pair badge is a fixture state,
   not a recorded pairing exchange. Laptop's Wake PC action sends no packets.
2. For shot 04, the video script begins on PCs and taps Living-room PC using its
   UIAutomator bounds. The real AppView grid appears with Racing Game running.
3. For shot 05, it starts in the library and taps Racing Game while recording.
4. Compact/advanced shots play the bundled flight sample. The overlay updates
   from the scripted source; retain the DEMO label and illustrative callouts.
5. Presets, scaling choices and mapping rows use the actual settings screens.
   The controller is a debug fixture; no physical controller is required.
6. Touch shows the real VirtualController; its gear enters move/resize modes.
7. PiP begins in the stream, then presses Home and verifies Android's pinned
   window. Use a neutral launcher background and Android 8+ with PiP support.
8. Shot 13 shows changing synthetic metrics. Do not describe these numbers as
   measured latency, throughput, HDR output or upscaling performance.

The source clip is 1080p60 SDR. The overlay's AV1 10-bit HDR / FSR 1 / 120 FPS
profile is illustrative. Use a real host session for benchmark claims.
`out/demo/sources.json` from the renderer records each actual capture used.
