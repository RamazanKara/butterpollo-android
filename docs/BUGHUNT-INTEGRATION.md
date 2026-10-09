# Bug-hunt integration repair

Integration base: `51f2b4aa` on `codex-int`, 2026-10-09. No commits, pushes, PRs, adb commands or emulator runs.

## Audit sources and coverage

The sibling worktree HEADs had already been rebased onto one another, including the broken conflict resolutions. Their reflogs retained the original commits based on `10ba65e8`; those original trees and patches were used for the comparison.

| Lane | Original commit | Report findings checked |
| --- | --- | --- |
| B1 | `74c583a8` | `BUGHUNT-stream.md`: S01–S19 |
| B2 | `c0368d79` | `BUGHUNT-INPUT.md`: 1–36 |
| B3 | `ab1feeab` | `BUGHUNT-networking.md`: N01–N35 |
| B4 | `936f471d` | `BUGHUNT-LIFECYCLE.md`: L01–L16 |
| B5 | `3e3c11a4` | `BUGHUNT-overlay-metrics-diagnostics-crash-launchers.md`: B01–B20 |
| B6 | `1c745a59` | `BUGHUNT-build-quality-security.md`: 1–15 |
| U1 | `3b551d6f` | UI consolidation, resources, role/preset tests and smoke-script changes |

All 141 recorded findings were checked, including overlapping reports of the same defect. The lane commits touch 149 distinct files, including 31 shared files. Every file outside documentation touched by just one lane already matched its original lane version. For the shared files, additions and deletions were compared with each original version, then overlapping implementations were checked for equivalent behavior. This is a source/integration audit; the device-only limitations in the original reports still apply.

## Reconciled shared files

Paths below are relative to `app/src` unless another root is shown. Rows marked retained required no production change after comparison.

| Files | Reconciliation |
| --- | --- |
| `main/java/com/limelight/ShortcutTrampoline.java` | Removed two surviving conflict markers and an outer duplicate `_computer` declaration. Kept name-only database opening with `finally` closure, single unbind ownership, and B5's main-thread missing-host/cancellation handling (N33–34, L02–03, B02–04, B6 #3). |
| `main/java/com/limelight/binding/video/PyroWaveDecoderRenderer.java`; `main/jni/pyrowave-renderer/pyrowave_renderer.cpp` | Unified S04/B14 on B5's native release timestamp. Restored the missing Java native declaration, reset the timestamp for every submission, and publish the timestamp taken before `vkQueuePresentKHR` only on success/suboptimal. Zero means no release; removed the redundant presentation boolean/JNI getter. Retained the 8,192-entry native pending queue. |
| `main/java/com/limelight/binding/video/FrameLatencyStats.java`; `test/java/com/limelight/binding/video/FrameLatencyStatsTest.java` | Retained the 8,192 pending / 4,096 CSV bounds and B1's 5,000-frame and overflow tests. Restored B5's distinct 8,000-frame test with periodic expiry (S03/B13). |
| `main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java` | Retained AVC-null guarding, AV1 crash-retry RFI disable, overlay-independent statistics, and exception cause chaining (S01–02, B15–16). |
| `main/java/com/limelight/computers/ComputerManagerService.java` | Kept B3's compare-and-set reference acquisition and atomic final release, removing the redundant B4 release monitor. Poll/removal references stay in `finally`; normal-LAN/VPN STUN cleanup and case-insensitive UUID lookup remain (N21–23, L04–05, B05). |
| `main/java/com/limelight/nvstream/mdns/NsdManagerDiscoveryAgent.java` | Retained `(serviceName, Network)` identity and callback delivery outside the listener lock, satisfying both B3 and B4 (N15–16, L06). |
| `main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java` | Retained B2's owned/cancelled recapture runnable, current focus/capture/cursor checks and destruction cleanup; these cover B4's delayed-callback guard (B2 #15, L11). |
| `main/java/com/limelight/utils/CacheHelper.java`; `test/java/com/limelight/utils/CacheHelperTest.java` | Combined B3's explicit UTF-8 and exact-length acceptance with B5's unconditional closure while ignoring close-only failures. Kept B3's equivalent read-error/size tests and added a close-failure regression (N25–26, B19–20). |
| `main/java/com/limelight/utils/ShortcutHelper.java` | Chose B5's shrinking mutable eviction snapshot, including zero-quota termination, over repeated service requery. B4's quota regression remains (L01/B01). |
| `main/java/com/limelight/utils/FrontendEntry.java`; `test/java/com/limelight/utils/FrontendEntryTest.java` | Retained B6's bounded character reader/BOM handling and B5's XML identity merge, malformed-XML rejection, app identity and UTF-8 filename limits. Restored the lost `StandardCharsets` test import (B06–11, B6 #1–2). |
| `root/java/com.limelight/binding/input/evdev/EvdevCaptureProvider.java` | Retained B2's full startup/cleanup, UTF-8 SU commands, loopback binding, accept/SU-exit handling and main-thread dispatch together with B4's shutdown publication checks. Removed a duplicate import (B2 #31–33, L12/L15, B6 #8). |
| `root/java/com.limelight/binding/input/evdev/EvdevReader.java`; `testRoot/java/com/limelight/binding/input/evdev/EvdevReaderTest.java` | Kept B6's invalid-length `null` result and both accepted native layouts. Restored the logging import removed by B2's alternative throwing implementation, and B2's distinct truncated-payload regression (B2 #30, B6 #9). |
| `main/jni/moonlight-core/callbacks.c` | Retained one JNI local-reference cleanup per array, B1 allocation checks and decoded audio sample count, and B2's explicit controller/sensor JNI casts (S07–08, B2 #9, L10, B6 #5–6). |
| `main/java/com/limelight/nvstream/jni/MoonBridge.java`; `main/jni/moonlight-core/simplejni.c`; `main/java/com/limelight/nvstream/NvConnection.java` | Retained the matching Java/native PCM length and UTF-8 byte-array contracts, stopped-connection input guard and U1's permission-checked quit action (S08, L07, B6 #7). |
| `main/jni/evdev_reader/evdev_reader.c` | Retained one atomic grab state and thread detach, plus B2's complete sends, ioctl validation and initial-grab serialization (B2 #34–36, L13–14, B6 #10–11). |
| `main/jni/moonlight-core/android_rtsp.c` | Retained B1's handshake response/ENet cleanup and audio-negotiation failure propagation with B3's crypto-allocation guards (S12–13, N11). The B3 Android parser already implements S14–15/N12–13. |
| `main/java/com/limelight/Game.java` | Retained all input ownership/release fixes, PyroWave label, power-lock/screen cleanup, main-thread root keyboard handling, replacement intents, per-host crash recovery and synchronized crash accounting alongside U1's stream action sheets (S16, B2 input findings, L08/L12/L16, B17–18). |
| `main/java/com/limelight/PcView.java`; `main/java/com/limelight/AppView.java` | Retained export-picker recreation state and app-list snapshots alongside U1's UI changes (L09, N24). |
| `main/java/com/limelight/preferences/AddComputerManually.java`; `main/java/com/limelight/preferences/PreferenceConfiguration.java` | Retained subnet masks and per-host crash-profile reset alongside U1's manual-add UI and preset behavior (N09, B17). |
| `main/AndroidManifest.xml` | Retained B6's manifest-level install policy together with U1's activity theme changes (B6 #13). |
| Repository `scripts/emulator-smoke.py` | Restored U1's Advanced → Overlay & audio navigation, viewport/font capture loop and font reset. Kept B6's initially-disabled ADPF expectation and debug-only overlay activity guard (B6 #14–15). Syntax checked only. |

## Native fixes omitted by the original integration

The populated `moonlight-common-c` directory has no `.git` metadata in this worktree. Its parent entry is a gitlink, so source edits inside it are invisible to the parent `git diff`. These fixes had not reached this checkout even though their reports/patches had:

- B1 `AudioStream.c`: retain socket ownership during failed thread startup (S10).
- B1 `RtpAudioQueue.c`: convert the complete reorder deadline to microseconds (S09).
- B1 `VideoDepacketizer.c`: free a decode unit rejected during queue shutdown (S11).
- B1 `RtspParser.c`: duplicate-header ownership and truncated-header bounds (S14–15); the application's compiled Android parser also retains B3's equivalent fixes.
- B1 `ControlStream.c`: complete legacy packet tables, reject runt HDR/termination packets and initialize all ping bytes (S17–19).
- B2 `InputStream.c`: clear/invalidate cached controller packets, use the matching absolute-position inverse scale, and reject motion type zero (B2 #26–28).

All six source files are patched on disk. B1's existing `main/jni/moonlight-core/patches/bughunt-stream.patch` was applied. B2's patch, previously embedded only in its report and an ignored build artifact, is now also preserved as `main/jni/moonlight-core/patches/bughunt-input.patch`. Both patches pass reverse-apply checks against the working sources.

When transferring this change to a fresh copy of the pinned native baseline `874ac9548f1bd6f095ef2b435c42cdde460e7821`, apply these from the repository root before building. Do not reapply them to this already-patched worktree:

```powershell
git apply --ignore-space-change --directory=app/src/main/jni/moonlight-core/moonlight-common-c app/src/main/jni/moonlight-core/patches/bughunt-stream.patch
git apply --ignore-space-change app/src/main/jni/moonlight-core/patches/bughunt-input.patch
```

No source directory was read-only. Only build-cache access required a workaround: Gradle, Android and JVM user homes and a copy of dependency/Robolectric caches were placed under ignored `.gradle/integration-home`.

## Verification

Final run: **BUILD SUCCESSFUL**, JDK 17.0.20.1, Gradle 9.7.1. No dependencies or lint suppressions were added.

```powershell
$env:JAVA_HOME = 'C:\jdk17'
$env:GRADLE_USER_HOME = 'C:\src\bp-T5\.gradle\integration-home'
$env:ANDROID_USER_HOME = 'C:\src\bp-T5\.gradle\integration-home\.android'
$env:JAVA_TOOL_OPTIONS = '-Duser.home=C:\src\bp-T5\.gradle\integration-home'
& 'C:\Users\ramaz\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat' :app:assembleNonRootDebug :app:testNonRootDebugUnitTest :app:lintNonRootDebug :app:testRootDebugUnitTest :app:externalNativeBuildRootDebug --no-daemon --max-workers=2 --console=plain --continue
```

| Check | Result |
| --- | --- |
| `:app:assembleNonRootDebug` | Passed; native libraries built for all four configured ABIs, PyroWave for both supported 64-bit ABIs. |
| `:app:testNonRootDebugUnitTest` | 589 tests in 52 suites; 0 failures, errors or skips. |
| `:app:testRootDebugUnitTest` | 594 tests in 54 suites; 0 failures, errors or skips. |
| `:app:lintNonRootDebug` | Passed: 0 errors, 212 warnings. |
| `:app:externalNativeBuildRootDebug` | Passed for arm64-v8a, armeabi-v7a, x86 and x86_64. |
| Standalone native regressions | Seven executables passed: `rtsp_duplicate`, `audio_reorder`, `video_shutdown`, `pyrowave_frame`, `networking`, `rtsp_parser`, `stun`. `control_packet_types` compile-time assertions also passed. Used the existing B1 LLVM/MinGW toolchain and NDK 29 host compiler; no installation. |
| Patch/source checks | Both native patches reverse-apply cleanly; `git diff --check` passed; no source conflict markers remain. |
| Smoke script | Python syntax passed without executing the script. |

Final build log: `build/integration/verification.log`. Unit-test XML is under `app/build/test-results/test{NonRoot,Root}DebugUnitTest`; lint reports are under `app/build/reports/lint-results-nonRootDebug.*`. The native test binaries and comparison inventory are under ignored `build/integration`.

The initial build reported 15 syntax errors from the trampoline markers. Subsequent reconciliation fixed the missing PyroWave JNI declaration, missing Root `LimeLog` import and missing `StandardCharsets` test import. Default-cache lock failures and a shared-cache JAR access error were resolved using local caches. Gradle deprecation/release-signing warnings and the 212 nonfatal lint findings remain; device-only exercises remain unrun as requested.
