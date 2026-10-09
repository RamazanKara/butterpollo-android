# Rubylight bug hunt: round 2

2026-10-09. Reviewed the requested subsystems in `2463d3d2..d2a6ecab`, the changes from the 24 hours preceding the audit. Application, package and resource identifiers are unchanged. No dependencies were added.

## Confirmed and fixed

1. **Input can reach JNI after disconnect while waiting for the batcher.** In `NvConnection`, mouse buttons, keyboard events and all four scroll methods checked `canSendInput()` before acquiring the batcher's monitor. If shutdown won that monitor, the waiting event subsequently sent without rechecking, potentially reaching a later native session. Each boundary callback now rechecks permission and shutdown state while holding the batcher monitor, matching the existing batched movement/controller callbacks.

   `NvConnectionTest.inputWaitingForABatchCannotSendAfterStop` holds the monitor, waits for an input thread to block, stops the connection, then releases it. Before the fix it failed because the stopped connection attempted JNI (`UnsatisfiedLinkError` in the JVM test). It covers all seven affected event methods.

2. **Draining decoded frames loses output-format notifications.** The inner dequeue loop in `MediaCodecDecoderRenderer` could consume `INFO_OUTPUT_FORMAT_CHANGED`, but only the outer dequeue's negative-result branch handled it. This left the saved format stale and bypassed the newly added upscaling compatibility/fallback check. Format handling now runs after either dequeue path, preserving the preceding frame's release order.

   `MediaCodecOutputFormatTest.drainingQueuedFramesStillProcessesFormatChanges` drives the real renderer loop with a Robolectric codec shadow returning a frame followed by a format notification. Before the fix it failed with a null saved format. This verifies notification handling; it does not simulate GPU rendering.

## Review coverage

Read the new classes and their tests in the requested areas, plus the changed integration paths:

| Area | Checks |
| --- | --- |
| Upscaling | `GlesUpscaler`, `UpscalingPolicy`, `UpscalingFrameQueue`, FSR/SGSR constants and shaders; EGL ownership, SurfaceTexture transforms, timestamp/fence queues, partial initialization, decoder-before-EGL teardown, rotation/PiP and surface destruction, HDR/size/format fallback. |
| Latency | `InputBatcher`, `MouseDeltaAccumulator`, decoder queue/pacing/display/performance-hint policies, `AudioBufferPolicy`, Java audio and native AAudio, adaptive/PyroWave bitrate controllers, benchmark activity/statistics and connection-test statistics; monitor ordering, cancellation, callback ownership, buffer bounds, ms/us/ns and bitrate units. |
| Crash reports | Application installation, `CrashCapture`, `CrashRecord`, `RedactedLog`, `ProblemReport` and debug crash activity; handler chaining, bounded/cyclic traces, native exit records, redaction, storage and URI grants. |
| Overlay/resources | `PerformanceOverlay` compact/advanced formatting, EN/DE strings and arrays, format arguments, missing keys, placeholders and plural branches. The export plural has matching `%1$d` arguments for `one` and `other`. Literal percentages in the sharpness explanation are not format placeholders. |
| Settings | Presets, per-PC profiles/settings, material preferences and search; migration/defaults, inherited overrides, validation, HDR/pacing/upscaling recomputation and bitrate units. |
| Launchers | Frontend entries/exporter, shortcuts/trampoline and lifecycle tests; bounded input, XML merging, URI handling, host identity, shortcut quotas and service binding cleanup. |
| USB | `DualSenseController`, `DualSenseReport`, USB service and controller integration; report lengths, calibration/motion units, trigger effects, output serialization, stop/disconnect races and permission receivers. |

API guards were reviewed from minSdk 21 through Android 16, especially EGL/SurfaceTexture ownership, display pacing, AAudio availability, exit-info APIs and USB receiver/PendingIntent behavior. No additional confirmed defects were found in these checks.

Platform contracts consulted: [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec), [SurfaceTexture](https://developer.android.com/reference/android/graphics/SurfaceTexture), [Display presentation deadlines](https://developer.android.com/reference/android/view/Display#getPresentationDeadlineNanos()) and [AAudio callback/close behavior](https://developer.android.com/ndk/guides/audio/aaudio/aaudio).

## Verification

Both regression tests were added and observed failing before production edits: 17 focused tests ran, with exactly those two failures. After the fixes, all three requested tasks passed together (`BUILD SUCCESSFUL`, 8m 19s):

| Task | Result |
| --- | --- |
| `:app:assembleNonRootDebug` | PASS; native builds for arm64-v8a, armeabi-v7a, x86 and x86_64. |
| `:app:testNonRootDebugUnitTest` | PASS; 645 tests across 66 suites, zero failures/errors/skips. |
| `:app:lintNonRootDebug` | PASS; zero errors, 198 warnings. No lint configuration or suppressions changed. |

`git diff --check` also passed.

Validation uses PowerShell, `JAVA_HOME=C:\jdk17`, the cached Gradle 9.7.1 distribution matching the wrapper, and `--no-daemon --max-workers=2 --console=plain --offline`:

```text
:app:assembleNonRootDebug :app:testNonRootDebugUnitTest :app:lintNonRootDebug
```

The default cache locations were not writable. Gradle dependencies were copied into the workspace using read-only source streams; Android and Java user-home paths were redirected into ignored build directories. Robolectric fetched its existing Android runtime dependencies from Maven Central into that writable home. No build configuration was changed. Logs: `build/bughunt-round2-before.log` and `build/bughunt-round2-validation.log`.

## Remaining device verification

No adb, emulator or real-device execution was performed. Actual vendor EGL/shader behavior, rotation/PiP during streaming, surface loss during GPU work, audible AAudio underrun/disconnect behavior, and physical DualSense calibration/haptics still need device testing. JVM policy/lifecycle tests and native compilation cannot establish those outcomes. No commit, push, PR or publication was performed.
