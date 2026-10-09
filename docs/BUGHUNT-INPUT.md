# INPUT bug hunt

Audit date: 2026-10-09. Base: `10ba65e8` (`main`). Scope: this Android worktree only; the host repository was not accessed or changed. No commit, push, PR, adb or emulator operation was performed.

Read `docs/PARITY-MATRIX.md`, `docs/TROUBLESHOOTING.md`, `git log -30`, and the input sources listed below. Recent input changes reviewed included `caccb3bc`, `62003dd5`, `009492ac`, `1b021a7d` and `9f57aa35`. There are no Kotlin input sources in this area.

## Results and verification

36 fixed findings are recorded below; related failure paths are grouped within a finding. Changes are uncommitted. No dependencies were added.

- `:app:assembleNonRootDebug`: passed.
- `:app:testNonRootDebugUnitTest`: 525 tests, 0 failures/errors/skips.
- `:app:lintNonRootDebug`: 0 errors, 176 warnings.
- Additional `:app:testRootDebugUnitTest`: 530 tests, 0 failures/errors/skips.
- Additional `:app:externalNativeBuildRootDebug`: passed. NonRoot and Root native builds cover arm64-v8a, armeabi-v7a, x86 and x86_64.
- `git diff --check`: passed.

All Gradle runs used PowerShell, `JAVA_HOME=C:\jdk17`, Gradle 9.7.1, `--no-daemon --max-workers=2`. Verification used the installed Gradle executable, `--offline --continue`, a workspace-local Gradle cache copied from the existing cache, and a workspace-local `ANDROID_USER_HOME`. The sandbox blocked writes to the default global cache; the local cache resolved this. Android analytics still emits a nonfatal warning about `C:\.android`. Final build log: `build/bughunt/verification.log`.

Added 15 JVM tests: 10 shared tests (`ControllerHandlerTest`, `XboxOneControllerTest`, `AnalogStickTest`, `GameInputTest`, and the new `KeyboardTranslatorTest` case), plus 5 Root-only tests (`EvdevReaderTest`, `EvdevTranslatorTest`). Nine new test methods were observed failing before their corresponding fixes: unsigned trigger selection, two rumble checks, vertical stick direction, keypad equals, three evdev checks, and modifier ownership. Existing input suites, including captured DualSense USB reports and all 256 trigger-effect types, also pass.

This checkout configures plain JUnit and kxml tests, not Robolectric, despite the task's stated assumption. Android Handler/View/USB/Binder paths were therefore checked by source-level state/resource/lock traces and compilation; they are not claimed as device-tested. Native runtime fault injection and end-to-end JNI text delivery were not run. Their remaining checks are explicit below.

## Fixed findings

| ID | Severity | Bug | Root cause | Minimal fix | Test / verification |
|---|---|---|---|---|---|
| 1 | Medium | Merged analog inputs invented axis/trigger values. | ControllerHandler.sendControllerInputPacket combined an already selected maximum with bitwise OR. | Assign the selected value in all Android, USB and OSC merge branches. | Source trace with split controllers; magnitude regressions; JVM/build. |
| 2 | Medium | Full trigger presses could lose to weaker presses. | The byte overload of maxByMagnitude compared signed magnitudes even though triggers are unsigned 0–255. | Compare unsigned byte values. | ControllerHandlerTest: unsigned boundaries reproduced red; signed stick boundaries remain covered. |
| 3 | Medium | Many-to-one Android mappings released a still-held target. | Each physical key-up cleared the target regardless of other mapped sources. | Track physical scan codes (key-code fallback), merge target ownership, and normalize Menu/Start and Back/Select aliases. | Three ControllerHandlerTest cases cover both release orders, repeats, aliases and independent controllers. |
| 4 | Medium | Android device reconfiguration lost held input and mouse mode. | migrateContext copied slot/sensor/light state but discarded buttons, axes, pending combinations and mouse ownership. | Carry active input state and timers' reference times into the replacement context; transfer mouse-button ownership before destroying the old context. | Source trace through pointer-capture device changes; compile. Hardware reconfiguration remains a device check. |
| 5 | Medium | A seventeenth controller could control player one. | Exhausting the 16-bit slot mask fell through with controllerNumber = 0. | Leave the context unassigned when full; also stop associated-device propagation and packet sending until a slot is available. | Source trace for a full mask, associated touchpad, and subsequent freed slot; compile. |
| 6 | Medium | Unassigned devices could receive player-one feedback or report its battery. | Default controllerNumber is zero; several feedback loops and migrated battery polling did not require assignment. | Require assignedControllerNumber and publish assignment with volatile visibility. | Source trace of context creation, migration, first report and host feedback; compile. |
| 7 | Medium | Feedback could restart effects while a controller was being destroyed. | Network-thread rumble/LED callbacks could overlap main-thread detach, migration and stop. | Serialize feedback and controller lifecycle mutations on the handler monitor; USB reports remain queued without acquiring that monitor on reader threads. | Lock-order review against USB driver locks and battery/light context locks; compile. Runtime detach stress remains pending. |
| 8 | Medium | Gyro/accelerometer reporting could restart while input was suspended. | Host enable requests and the delayed migration callback ignored sensorsEnabled. | Retain requested rates but gate registration, delayed enabling and late sensor delivery. | Source traces for pause → host request → migration → resume, and stop; compile. |
| 9 | Medium | Unsigned sensor rates could become negative or fail CheckJNI narrowing. | JNI passed uint16_t directly to a Java short, then Java clamped the signed value. | Narrow JNI arguments explicitly and clamp the unsigned Java value to Android's existing 200 Hz limit. | Boundary analysis for 0, 200, 32768 and 65535; native builds. No new high-rate permission. |
| 10 | High | USB input raced main-thread controller state and could precede registration. | Arrival was posted to the main handler, while state/touch/motion/battery callbacks ran directly on reader threads and shared mutable state. | Post all USB reports to the same handler, preserving registration/report order and checking service lifetime at delivery. | Source ordering and stop/rebind trace; all JVM suites and builds. USB timing stress requires hardware. |
| 11 | Medium | Xbox capabilities were empty and the Guide button could start held. | AbstractXboxController initialized buttonFlags with the supported-button mask. | Initialize supportedButtonFlags instead; live state starts clear. | Constructor plus Xbox One 0x20/0x07 report trace; compile. |
| 12 | Medium | Xbox One rumble jumped at half strength. | Arithmetic shifts sign-extended unsigned 16-bit motor values. | Mask all four motors before scaling; isolate existing packet construction for JVM testing. | Two XboxOneControllerTest regressions reproduced red; exhaustively check all 65,536 strengths on all motors. |
| 13 | Medium | Xbox shutdown/startup failure could leave effects, claimed interfaces or concurrent I/O cleanup. | Stop omitted trigger motors, startup failure only closed the connection, and stop visibility/serialization was incomplete. | Stop both motor pairs, release only successfully claimed interfaces on failed start, and serialize Xbox start/stop/output with a volatile stop flag. | Failure-stage/resource and reader/feedback lock-order traces; compile. Kernel reattachment and physical rumble stop need a device. |
| 14 | Medium | Xbox devices could be claimed twice and missed explicit detach cleanup. | USB identity checks handled DualSense only. | Use the same device-identity check for Xbox and DualSense during claim and detach. | Enumeration + delayed attachment + detach trace; compile. |
| 15 | Medium | A delayed focus callback could recapture a released/visible pointer. | The callback retained neither cancellation nor current capture/focus state. | Own and cancel the callback; recheck capture, cursor visibility, focus and compatible devices; disable capture on destroy. | Source trace for focus regain followed by menu, disable, focus loss or destruction; compile. |
| 16 | Medium | Old touch click releases could cancel a new drag or collapse rapid clicks. | Delayed button-up callbacks survived cancellation and overlapping taps. | Track pending releases, flush before the next press, and remove/release them on cancellation in both touch modes. | Timer/state traces for quick double taps, quick tap → drag and touch-mode changes; compile. Android Handler timing needs runtime validation. |
| 17 | Medium | The on-screen stick reversed exactly vertical movement. | getAngle's x == 0 branch disagreed with neighboring quadrants. | Correct the two vertical angles. | AnalogStickTest reproduced red; checks cardinal directions and continuity on both sides of vertical. |
| 18 | Medium | One finger could release another finger's on-screen button. | A directly pressed DigitalButton had no movement owner, so another button's release could clear it. | Mark a direct press as self-owned and clear ownership on release/cancel. | Source trace: hold B directly, release another finger over B; compile. Full multitouch sweep remains pending. |
| 19 | Medium | Supplementary Unicode text was corrupted at JNI. | GetStringUTFChars produces modified UTF-8, including surrogate encodings incompatible with the input protocol. | Encode standard UTF-8 in Java and pass a byte array with its byte length through JNI. | Existing Unicode/IME JVM tests pass; native builds verify signature changes. End-to-end emoji/IME delivery needs a host/device. |
| 20 | Low | Keypad equals was dropped. | KEYCODE_NUMPAD_EQUALS was absent from virtual-key translation. | Map it to the existing equals virtual key. | KeyboardTranslatorTest reproduced red. |
| 21 | Medium | Releasing one Shift/Ctrl/Alt/Meta side cleared the other side. | Both physical modifier keys shared one state bit. | Track each side separately and fold the bits only when producing protocol modifier flags. | GameInputTest reproduced red for independent side ownership; covers all four modifier pairs. |
| 22 | Medium | A stale shortcut could execute after focus/input was released. | releaseKeyboardInput cleared modifiers but retained the pending shortcut and delayed grab toggle. | Clear both shortcut fields and cancel the pending toggle. | Source trace for focus loss/menu during Ctrl+Alt+Shift combinations; compile. |
| 23 | Low | Stylus click deadzones failed on letterboxed/background touches. | Stored down/up coordinates were view-local while comparisons used stream-relative coordinates. | Store both positions in stream-relative coordinates. | Coordinate trace with a nonzero StreamView origin; compile. Pen hardware validation pending. |
| 24 | Medium | Gamepad mouse emulation could leave mouse buttons held. | Toggling or destroying the context canceled movement without releasing emulated A/B mouse buttons. | Release owned buttons and clear the remembered mask; migration explicitly transfers ownership. | Toggle/detach/migration state traces; compile. |
| 25 | Low | Very long-held controller buttons could cause an enormous UI-thread sleep. | A millisecond duration was narrowed from long to int before the minimum-press check. | Keep the duration as long. | Arithmetic trace beyond Integer.MAX_VALUE milliseconds; compile. |
| 26 | High | Native controller batching could reuse a stale/freed packet. | Cached holder pointers survived reconnect initialization or a failed queue insertion. | Clear the cache on initialization and invalidate a failed holder under the batching mutex before freeing it. | Ownership/lock trace; all four native ABIs compile. Queue-full/reconnect sanitizer exercise pending. |
| 27 | Medium | Absolute mouse emulation could stick at the right/bottom edge. | Position normalization used dimension − 1, but the inverse conversion used dimension. | Use the matching inverse scale and round to the nearest pixel. | Boundary trace for a one-pixel inward move from the edge and repeated round trips; four ABI builds. |
| 28 | Low | Motion type zero could index before the native sensor array. | Unsigned-byte promotion made motionType − 1 equal −1, which passed the upper-bound-only check. | Check both ends of the supported motion-type range. | Native boundary/index analysis and four ABI builds; malformed-call runtime injection not performed. |
| 29 | Medium | Legacy root keyboard Home was dropped; invalid negative codes could crash translation. | Home mapped to Android's launcher key, and lookup checked only the upper bound. | Use MOVE_HOME and validate both bounds. | Two EvdevTranslatorTest methods reproduced red. |
| 30 | Medium | Malformed evdev lengths could desynchronize input or request excessive allocation. | The reader accepted every size at least 16 bytes. | Accept only the 16-byte and 24-byte input_event layouts before reading the body. | EvdevReaderTest reproduced red; valid layouts and truncated-frame behavior are also tested. |
| 31 | High | Root mouse capture could fail to launch on Android 5/6. | DataOutputStream.writeChars wrote UTF-16 bytes, including NULs, into the SU shell command. | Write UTF-8 command bytes. | Byte-encoding/source trace; Root compile. SU implementation/device validation pending. |
| 32 | High | Root input setup could leak sockets/processes or hang shutdown/denial handling. | Resources could be published after destroy's snapshot; setup failures leaked resources, and accept did not observe a terminated SU process. The listener also bound every network interface. | Publish resources visibly, close all failure/exit paths, check shutdown after publication, poll for SU exit while accepting, and bind IPv4 loopback. | Publication/close/accept state-machine review; Root JVM/native builds. Physical SU denial and rapid exit tests remain pending. |
| 33 | High | Root keyboard shortcuts and mouse accumulation ran off the UI thread. | EvdevReader called Game directly from its worker thread. | Dispatch to the activity thread, gated by capture and provider lifetime. | Callback order/lifecycle trace and Root compile. |
| 34 | Medium | Native evdev forwarding could corrupt frame boundaries. | send() was assumed to transmit a whole packet. | Send the entire frame under the existing socket mutex, retry EINTR and shut down on failure. | Partial-write/error-path trace; four Root native ABI builds. |
| 35 | Medium | Failed capability ioctls could classify random devices as keyboards/mice. | Uninitialized bit masks were read after unchecked ioctl failures. | Initialize masks and reject failed queries. | Failure-path review; four Root native ABI builds. |
| 36 | Medium | Native evdev hotplug leaked thread resources and raced grab changes. | Reader threads were neither joined nor detached; initial grabs were unsynchronized with ungrab, and grabbing was shared non-atomically. | Detach readers, serialize initial grabs with the device-list lock, and make grab state atomic. | Thread/list/socket lock-order review; four Root native ABI builds. Hotplug/grab stress requires a rooted device. |

## Complete input source inventory

Every listed function body, constructor overload, field initializer, anonymous callback and nested type was inspected. Repeated method names are grouped for readability, including `run`, overloaded translators and constructors. Assessments cover null/error paths, thread ownership, lifecycle/resources, state transitions, signedness and units, API gates, permissions, hardware assumptions and locale/layout behavior. Shared files such as Game and the JNI bridge are scoped to their input and lifecycle portions; codec/rendering internals are outside this hunt.

### binding/input/capture/AndroidNativePointerCaptureProvider.java

Types: `AndroidNativePointerCaptureProvider`.

Functions: `AndroidNativePointerCaptureProvider`, `isCaptureProviderSupported`, `hasCaptureCompatibleInputDevice`, `showCursor`, `hideCursor`, `onWindowFocusChanged`, `destroy`, `eventHasRelativeMouseAxes`, `getRelativeAxisX`, `getRelativeAxisY`, `onInputDeviceAdded`, `onInputDeviceRemoved`, `onInputDeviceChanged`.

Finding 15. API-26 gate, touchscreen/ChromeOS compatibility, relative/historical axes and listener lifecycle reviewed.

### binding/input/capture/AndroidPointerIconCaptureProvider.java

Types: `AndroidPointerIconCaptureProvider`.

Functions: `AndroidPointerIconCaptureProvider`, `isCaptureProviderSupported`, `hideCursor`, `showCursor`.

API-24 gate and cursor restoration checked; no independent resource ownership.

### binding/input/capture/InputCaptureManager.java

Types: `InputCaptureManager`.

Functions: `getInputCaptureProvider`.

Provider priority and Android/root/SHIELD API gates checked.

### binding/input/capture/InputCaptureProvider.java

Types: `InputCaptureProvider`.

Functions: `enableCapture`, `disableCapture`, `destroy`, `isCapturingEnabled`, `isCapturingActive`, `showCursor`, `hideCursor`, `eventHasRelativeMouseAxes`, `getRelativeAxisX`, `getRelativeAxisY`, `onWindowFocusChanged`.

Capture/visibility state and default no-op contract checked.

### binding/input/capture/NullCaptureProvider.java

Types: `NullCaptureProvider`.

Functions: none beyond inherited behavior.

Inherited fallback contract checked; no additional methods.

### binding/input/capture/ShieldCaptureProvider.java

Types: `ShieldCaptureProvider`.

Functions: `ShieldCaptureProvider`, `isCaptureProviderSupported`, `setCursorVisibility`, `hideCursor`, `showCursor`, `eventHasRelativeMouseAxes`, `getRelativeAxisX`, `getRelativeAxisY`.

Reflection availability/failure and relative-axis event classification checked; proprietary firmware behavior remains a device check.

### binding/input/ControllerButtonMap.java

Types: `ControllerButtonMap`.

Functions: `isRemappableSource`, `isTarget`, `deviceKey`, `map`, `mapButtonFlags`, `buttonFlag`, `put`, `remove`, `isEmpty`, `entries`, `serialize`, `deserialize`, `load`, `save`.

Persistence bounds, disabled/identity/chained mappings, Locale.ROOT IDs and USB flag preservation checked. Existing map tests plus new Android held-target tests cover shared targets.

### binding/input/ControllerHandler.java

Types: `ControllerHandler`, `GenericControllerContext`, `InputDeviceContext`, `UsbDeviceContext`.

Functions: `ControllerHandler`, `getMotionRangeForJoystickAxis`, `onInputDeviceAdded`, `onInputDeviceRemoved`, `onInputDeviceChanged`, `stop`, `destroy`, `disableSensors`, `enableSensors`, `hasJoystickAxes`, `hasGamepadButtons`, `isGameControllerDevice`, `getAttachedControllerMask`, `releaseControllerNumber`, `isAssociatedJoystick`, `assignControllerNumberIfNeeded`, `createUsbDeviceContextForDevice`, `hasButtonUnderTouchpad`, `isExternal`, `shouldIgnoreBack`, `createInputDeviceContextForDevice`, `getContextForEvent`, `maxByMagnitude`, `getActiveControllerMask`, `updateMappedButton`, `areBatteryCapacitiesEqual`, `sendControllerBatteryPacket`, `sendControllerInputPacket`, `handleRemapping`, `handleFlipFaceButtons`, `populateCachedVector`, `handleDeadZone`, `handleAxisSet`, `normalizeRawValueWithRange`, `sendTouchpadEventForPointer`, `tryHandleTouchpadEvent`, `handleMotionEvent`, `convertRawStickAxisToPixelMovement`, `sendEmulatedMouseMove`, `sendEmulatedMouseScroll`, `hasDualAmplitudeControlledRumbleVibrators`, `rumbleDualVibrators`, `hasQuadAmplitudeControlledRumbleVibrators`, `rumbleQuadVibrators`, `rumbleSingleVibrator`, `handleRumble`, `handleRumbleTriggers`, `handleSetAdaptiveTriggers`, `createSensorListener`, `onSensorChanged`, `onAccuracyChanged`, `handleSetMotionEventState`, `handleSetControllerLED`, `handleButtonUp`, `handleButtonDown`, `reportOscState`, `reportControllerState`, `reportControllerTouch`, `reportControllerMotion`, `reportControllerBattery`, `deviceRemoved`, `deviceAdded`, `run`, `toggleMouseEmulation`, `releaseMouseButtons`, `sendControllerArrival`, `migrateContext`.

Findings 1–9, 24–25. Main-thread input state, network feedback/lifecycle monitor, background battery/light work and sensor callbacks reviewed; all device branches, slot masks and unsigned/unit conversions included.

### binding/input/driver/AbstractController.java

Types: `AbstractController`.

Functions: `AbstractController`, `getControllerId`, `getVendorId`, `getProductId`, `getSupportedButtonFlags`, `getCapabilities`, `getType`, `setButtonFlag`, `reportInput`, `start`, `stop`, `rumble`, `rumbleTriggers`, `setAdaptiveTriggers`, `setLed`, `setPlayerNumber`, `setMotionEventState`, `notifyDeviceRemoved`, `notifyDeviceAdded`.

Metadata defaults, bit updates, event dispatch and optional feedback no-ops checked.

### binding/input/driver/AbstractXboxController.java

Types: `AbstractXboxController`.

Functions: `AbstractXboxController`, `createInputThread`, `run`, `start`, `stop`, `handleRead`, `doInit`.

Findings 11, 13. All claim/init/read/timeout/stop paths and interface ownership checked.

### binding/input/driver/DualSenseController.java

Types: `DualSenseController`.

Functions: `DualSenseController`, `findHidInterface`, `canClaimDevice`, `getUsbDeviceId`, `readFeature`, `start`, `readInput`, `writeOutput`, `stop`, `rumble`, `rumbleTriggers`, `setAdaptiveTriggers`, `setLed`, `setPlayerNumber`, `setMotionEventState`.

Descriptor/endpoint checks, feature reads, calibration, synchronized output/stop, touch transitions, battery and unsigned nanosecond rate scheduling reviewed; ordering corrected in the service.

### binding/input/driver/DualSenseReport.java

Types: `DualSenseReport`, `Input`, `Calibration`, `Output`.

Functions: `Output`, `signedShort`, `parse`, `stick`, `reportTouchChanges`, `findTouch`, `apply`, `build`, `rumble`, `led`, `player`, `adaptiveTriggers`, `reset`.

Every Input/Calibration/Output method, packet offsets, signed sensor calibration, touch IDs, battery bounds, adaptive-trigger masks and reset examined. Existing captured-report and 256-effect tests pass.

### binding/input/driver/UsbDriverListener.java

Types: `UsbDriverListener`.

Functions: `reportControllerState`, `reportControllerTouch`, `reportControllerMotion`, `reportControllerBattery`, `deviceRemoved`, `deviceAdded`.

All callback payloads and default optional callbacks reviewed; delivery threading fixed in service.

### binding/input/driver/UsbDriverService.java

Types: `UsbDriverService`, `UsbEventReceiver`, `UsbDriverBinder`, `UsbDriverStateListener`.

Functions: `reportControllerState`, `reportControllerTouch`, `reportControllerMotion`, `reportControllerBattery`, `deviceRemoved`, `deviceAdded`, `onReceive`, `run`, `setListener`, `setStateListener`, `start`, `stop`, `isControllerForDevice`, `handleUsbDeviceState`, `isRecognizedInputDevice`, `kernelSupportsXboxOne`, `kernelSupportsXbox360W`, `shouldClaimDevice`, `onCreate`, `onDestroy`, `onUnbind`, `onBind`, `onUsbPermissionPromptStarting`, `onUsbPermissionPromptCompleted`.

Findings 10, 13–14. Enumeration, receiver export flags, mutable package-scoped permission intent, pending prompts, binding/unbinding and worker callbacks checked.

### binding/input/driver/Xbox360Controller.java

Types: `Xbox360Controller`.

Functions: `Xbox360Controller`, `canClaimDevice`, `unsignByte`, `handleRead`, `sendLedCommand`, `doInit`, `rumble`, `rumbleTriggers`.

Report size checks, signed axes, unsigned motor bytes, LED commands and trigger-rumble no-op checked; inherits Xbox lifecycle fixes.

### binding/input/driver/Xbox360WirelessDongle.java

Types: `Xbox360WirelessDongle`.

Functions: `Xbox360WirelessDongle`, `canClaimDevice`, `sendLedCommandToEndpoint`, `sendLedCommandToInterface`, `start`, `stop`, `rumble`, `rumbleTriggers`.

Interface/endpoint selection and LED-only ownership checked. Intentionally returns false after initialization so the service closes its connection; normal controller input remains with Android.

### binding/input/driver/XboxOneController.java

Types: `XboxOneController`, `InitPacket`.

Functions: `InitPacket`, `XboxOneController`, `processButtons`, `ackModeReport`, `handleRead`, `canClaimDevice`, `doInit`, `sendRumblePacket`, `createRumblePacket`, `rumble`, `rumbleTriggers`.

Finding 12 and synchronized output for 13. Initialization/ACK/report lengths, trigger/stick units and packet fields reviewed.

### binding/input/evdev/EvdevCaptureProviderShim.java

Types: `EvdevCaptureProviderShim`.

Functions: `isCaptureProviderSupported`, `createEvdevCaptureProvider`.

Flavor/API gating and reflection constructor/error propagation checked.

### binding/input/evdev/EvdevListener.java

Types: `EvdevListener`.

Functions: `mouseMove`, `mouseButtonEvent`, `mouseVScroll`, `mouseHScroll`, `keyboardEvent`.

All mouse/keyboard callback contracts checked; root delivery now occurs on main.

### binding/input/KeyboardTranslator.java

Types: `KeyboardTranslator`, `KeyboardMapping`.

Functions: `KeyboardMapping`, `KeyboardTranslator`, `getDeviceKeyCodeForQwertyKeyCode`, `getQwertyKeyCodeForDeviceKeyCode`, `hasNormalizedMapping`, `translate`, `textForCodePoint`, `sendTextInput`, `translateKeyCode`, `onInputDeviceAdded`, `onInputDeviceRemoved`, `onInputDeviceChanged`.

Finding 20. Normalized API-33 mappings, IME controls, dead keys, Unicode code points, right modifiers, numpad and unknown-key fallback checked; existing tests retained.

### binding/input/MouseDeltaAccumulator.java

Types: `MouseDeltaAccumulator`.

Functions: `scale`.

Fractional carry and signed rounding checked with existing tests; no additional defect proved.

### binding/input/touch/AbsoluteTouchContext.java

Types: `AbsoluteTouchContext`.

Functions: `AbsoluteTouchContext`, `run`, `getActionIndex`, `touchDownEvent`, `distanceExceeds`, `updatePosition`, `touchUpEvent`, `startLongPressTimer`, `cancelLongPressTimer`, `startTapDownTimer`, `cancelTapDownTimer`, `tapConfirmed`, `touchMoveEvent`, `cancelTouch`, `isCancelled`, `setPointerCount`.

Finding 16. Every timer, cancellation, double-tap/long-press/scroll transition and pixel/reference conversion reviewed.

### binding/input/touch/RelativeTouchContext.java

Types: `RelativeTouchContext`.

Functions: `RelativeTouchContext`, `run`, `getActionIndex`, `isWithinTapBounds`, `isTap`, `getMouseButtonIndex`, `touchDownEvent`, `touchUpEvent`, `startDragTimer`, `cancelDragTimer`, `releasePendingButtonUp`, `checkForConfirmedMove`, `checkForConfirmedScroll`, `touchMoveEvent`, `cancelTouch`, `isCancelled`, `setPointerCount`.

Finding 16. Tap/drag/scroll transitions, pending releases, subpixel scaling and absolute-mode deltas reviewed; primary-finger handoff remains a device follow-up.

### binding/input/touch/TouchContext.java

Types: `TouchContext`.

Functions: `getActionIndex`, `setPointerCount`, `touchDownEvent`, `touchMoveEvent`, `touchUpEvent`, `cancelTouch`, `isCancelled`.

All action-index, pointer-count, down/move/up/cancel contracts checked against Game.

### binding/input/virtual_controller/AnalogStick.java

Types: `AnalogStick`, `AnalogStickListener`, `STICK_STATE`, `CLICK_STATE`.

Functions: `AnalogStick`, `onMovement`, `onClick`, `onDoubleClick`, `onRevoke`, `getMovementRadius`, `getAngle`, `addAnalogStickListener`, `notifyOnMovement`, `notifyOnClick`, `notifyOnDoubleClick`, `notifyOnRevoke`, `onSizeChanged`, `onElementDraw`, `updatePosition`, `onElementTouchEvent`.

Finding 17. Angle/radius, deadzone, size changes, movement and click/double-click/revoke transitions reviewed.

### binding/input/virtual_controller/DigitalButton.java

Types: `DigitalButton`, `DigitalButtonListener`.

Functions: `DigitalButton`, `onClick`, `onLongClick`, `onRelease`, `run`, `inRange`, `checkMovement`, `checkMovementForAllButtons`, `addDigitalButtonListener`, `setText`, `setIcon`, `onElementDraw`, `onClickCallback`, `onLongClickCallback`, `onReleaseCallback`, `onElementTouchEvent`.

Finding 18. Hit bounds, slide ownership, long-click timer and up/cancel paths reviewed.

### binding/input/virtual_controller/DigitalPad.java

Types: `DigitalPad`, `DigitalPadListener`.

Functions: `DigitalPad`, `addDigitalPadListener`, `onElementDraw`, `newDirectionCallback`, `onElementTouchEvent`, `onDirectionChange`.

Direction bits, drawing bounds and cancel/release transitions checked.

### binding/input/virtual_controller/LeftAnalogStick.java

Types: `LeftAnalogStick`.

Functions: `LeftAnalogStick`, `onMovement`, `onClick`, `onDoubleClick`, `onRevoke`.

Movement and L3 click/double-click/revoke callbacks checked for correct axis/button polarity.

### binding/input/virtual_controller/LeftTrigger.java

Types: `LeftTrigger`.

Functions: `LeftTrigger`, `onClick`, `onLongClick`, `onRelease`.

Press/release trigger bytes and no-op long press checked.

### binding/input/virtual_controller/RightAnalogStick.java

Types: `RightAnalogStick`.

Functions: `RightAnalogStick`, `onMovement`, `onClick`, `onDoubleClick`, `onRevoke`.

Movement and R3 click/double-click/revoke callbacks checked for correct axis/button polarity.

### binding/input/virtual_controller/RightTrigger.java

Types: `RightTrigger`.

Functions: `RightTrigger`, `onClick`, `onLongClick`, `onRelease`.

Press/release trigger bytes and no-op long press checked.

### binding/input/virtual_controller/VirtualController.java

Types: `VirtualController`, `ControllerInputContext`, `ControllerMode`.

Functions: `VirtualController`, `run`, `onClick`, `getHandler`, `hide`, `show`, `removeElements`, `setOpacity`, `addElement`, `getElements`, `_DBG`, `refreshLayout`, `getControllerMode`, `setControllerMode`, `releaseInput`, `getControllerInputContext`, `sendControllerInputContextInternal`, `sendControllerInputContext`.

ControllerInputContext fields, mode changes, delayed retransmits, hide/remove/release, opacity and layout rebuild examined; no new layout model introduced.

### binding/input/virtual_controller/VirtualControllerConfigurationLoader.java

Types: `VirtualControllerConfigurationLoader`.

Functions: `getPercent`, `screenScale`, `createDigitalPad`, `onDirectionChange`, `createDigitalButton`, `onClick`, `onLongClick`, `onRelease`, `createLeftTrigger`, `createRightTrigger`, `createLeftStick`, `createRightStick`, `createDefaultLayout`, `saveProfile`, `loadFromPreferences`.

All factories/listeners, geometry scaling, profile serialization and malformed-profile fallback checked; foldable/custom-layout follow-up remains.

### binding/input/virtual_controller/VirtualControllerElement.java

Types: `VirtualControllerElement`, `Mode`.

Functions: `VirtualControllerElement`, `moveElement`, `resizeElement`, `onDraw`, `actionEnableMove`, `actionEnableResize`, `actionCancel`, `getDefaultColor`, `getDefaultStrokeWidth`, `showConfigurationDialog`, `onClick`, `onTouchEvent`, `onElementDraw`, `onElementTouchEvent`, `_DBG`, `setColors`, `setOpacity`, `getPercent`, `getCorrectWidth`, `getConfiguration`, `loadConfiguration`.

Configuration dialog/touch handling, resize/move limits, opacity, drawing and JSON fields checked; commented-out color-picker code is not an active function.

### preferences/ConfirmDeleteOscPreference.java

Types: `ConfirmDeleteOscPreference`.

Functions: `ConfirmDeleteOscPreference`, `onClick`.

All constructor overloads and confirmed profile reset/cancel behavior checked.

### preferences/ControllerMappingActivity.java

Types: `ControllerMappingActivity`.

Functions: `onCreate`, `selectSingleConnectedController`, `select`, `refresh`, `row`, `primaryTextColor`, `accentColor`, `isControllerEvent`, `dispatchKeyEvent`, `chooseTarget`, `targetName`, `buttonName`.

Controller selection, digital-source filtering, waiting-key state, dialogs, disabled/default targets and refresh behavior checked.

### ui/GameGestures.java

Types: `GameGestures`.

Functions: `toggleKeyboard`.

Keyboard-toggle contract and controller shortcut caller checked.

### ui/StreamView.java

Types: `StreamView`, `InputCallbacks`.

Functions: `StreamView`, `initializeSurface`, `getHolder`, `getTextureView`, `setDesiredAspectRatio`, `setInputCallbacks`, `onCheckIsTextEditor`, `onCreateInputConnection`, `commitText`, `deleteSurroundingText`, `deleteSurroundingTextInCodePoints`, `sendKeyPress`, `onMeasure`, `onKeyPreIme`, `handleKeyUp`, `handleKeyDown`, `handleTextInput`.

Both surface modes, all InputCallbacks and anonymous BaseInputConnection methods, editor/IME flags, text/deletion, pre-IME keys and sizing checked; existing Unicode/text tests retained.

### utils/Vector2d.java

Types: `Vector2d`.

Functions: `Vector2d`, `initialize`, `getMagnitude`, `getNormalized`, `scalarMultiply`, `setX`, `setY`, `getX`, `getY`.

Magnitude/normalization, initialization and mutators checked together with deadzone callers; zero vectors are handled before normalization there.

### root/binding/input/evdev/EvdevCaptureProvider.java

Types: `EvdevCaptureProvider`.

Functions: `EvdevCaptureProvider`, `run`, `dispatch`, `closeResources`, `reportDeviceNotRooted`, `runInNetworkSafeContextSynchronously`, `showCursor`, `hideCursor`, `enableCapture`, `destroy`.

Findings 31–33. Worker startup, SU command/denial, socket publication, dispatch, grab/ungrab and destruction reviewed.

### root/binding/input/evdev/EvdevEvent.java

Types: `EvdevEvent`.

Functions: `EvdevEvent`.

Both native event sizes and event/axis/button constants checked.

### root/binding/input/evdev/EvdevReader.java

Types: `EvdevReader`.

Functions: `readAll`, `read`.

Finding 30. Full reads, byte order, timestamp widths and frame bounds checked.

### root/binding/input/evdev/EvdevTranslator.java

Types: `EvdevTranslator`.

Functions: `translateEvdevKeyCode`.

Finding 29. Entire evdev table and lookup bounds checked.

### Shared activity, preferences and protocol code

- `Game`: USB service callbacks; input setup in `onCreate`; `onConfigurationChanged`, `setPreferredOrientationForCurrentDisplay`, PiP/focus hooks, `setMetaKeyCaptureState`, `onWindowFocusChanged`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onBackPressed`; `showStreamDialog`, `showStreamMenu`, `initializeTouchContexts`, `cancelTouchInput`, `showTouchModeDialog`, `setOnscreenControlsEnabled`, `showControllerLayoutDialog`; `setInputGrabState`, `getModifierMask`, `handleSpecialKeys`, all `getModifierState` overloads, `applyKeySpecificModifiers`; `onKeyDown`, `handleKeyDown`, `onKeyUp`, `handleKeyUp`, `onKeyMultiple`, `handleKeyMultiple`, `handleTextInput`, `getTouchContext`, `toggleKeyboard`; `getLiTouchTypeFromEvent`, `getStreamViewRelativeNormalizedXY`, `normalizeValueInRange`, `getPressureOrDistance`, `getRotationDegrees`, `polarToCartesian`, `cartesianToR`, `getStreamViewNormalizedContactArea`, `sendPenEventForPointer`, `convertToolTypeToStylusToolType`, `trySendPenEvent`, `sendTouchEventForPointer`, `trySendTouchEvent`, `handleMotionEvent`, `onGenericMotionEvent`, `updateMousePosition`, `onGenericMotion`, `onTouch`; connection start/stop/failure hooks; `rumble`, `rumbleTriggers`, `setMotionEventState`, `setControllerLED`, `setAdaptiveTriggers`; `mouseMove`, `mouseButtonEvent`, `mouseVScroll`, `mouseHScroll`, `keyboardEvent`, `sendKeyboardInput`, `releaseKeyboardInput`, `releaseMouseButtons`, USB permission hooks and `onKey`. All associated delayed runnables and lambdas were included. Findings 19, 21–23 apply; focus/menu/native-touch cancellation and relative/absolute routing were also traced.
- `WindowFocusActionQueue`: `add`, `runPending`, `clear`, deadline and deque ownership. Main-thread use, cancellation from an action and old-timeout behavior match its existing tests; no change.
- `NvConnection`: `canSendInput`, lifecycle gating and every `sendMouse*`, `sendKeyboardInput`, `sendController*`, `sendTouchEvent`, `sendPenEvent`, `sendUtf8Text` forwarding function. Remote-monitor/permission/monkey restrictions remain intact; finding 19 changes only text encoding.
- `MoonBridge`: every native input declaration and `bridgeClRumble`, `bridgeClRumbleTriggers`, `bridgeClSetMotionEventState`, `bridgeClSetControllerLED`, `bridgeClSetAdaptiveTriggers`, listener setup/cleanup and null listener gates. Checked Java/JNI widths and callbacks; findings 9 and 19.
- `ControllerPacket`, `KeyboardPacket`, `MouseButtonPacket`: all button, direction, modifier and packet constants; no methods. OSC's signed short flags are handled by the native legacy sign-extension compatibility path.
- `PreferenceConfiguration`: input constants/fields, `getAnalogStickForScrollingValue` and input portions of both `readPreferences` overloads, including mouse/trackpad clamps, USB opt-in, face flip, sensor fallback and Android-12 default. Also inspected the stream layout, manifest permissions/API gates and native build configuration. Existing preferences and protocol capability policy are unchanged.

### Native input inventory

- `android_input.c`: `sleepInput` and the scoped input-sender include/override. Only the existing 1 ms batching sleep is bypassed; keyboard synchronization delays remain.
- `InputStream.c`: `initializeInputStream`, `destroyInputStream`, `encryptData`, `freePacketHolder`, `allocatePacketHolder`, `sendInputPacket`, `floatToNetfloat`, `inputSendThreadProc`, `sendEnableHaptics`, `startInputStream`, `stopInputStream`, `LiSendMouseMoveEvent`, `LiSendMousePositionEvent`, `LiSendMouseMoveAsMousePositionEvent`, `LiSendMouseButtonEvent`, `LiSendKeyboardEvent2`, `LiSendKeyboardEvent`, `LiSendUtf8TextEvent`, `sendControllerEventInternal`, `LiSendControllerEvent`, `LiSendMultiControllerEvent`, `LiSendHighResScrollEvent`, `LiSendScrollEvent`, `LiSendHighResHScrollEvent`, `LiSendHScrollEvent`, `LiSendTouchEvent`, `LiSendPenEvent`, `LiSendControllerArrivalEvent`, `LiSendControllerTouchEvent2`, `LiSendControllerTouchEvent`, `LiSendControllerMotionEvent`, `LiSendControllerBatteryEvent`. All packet-holder, batch, queue, lifetime, unit and failure branches reviewed; findings 26–28. `Input.h` packet layouts/constants were read completely.
- `simplejni.c`: every mouse, controller, keyboard, text, touch, pen, battery/motion/arrival input export, including argument signedness, array ownership and return propagation; controller guessing exports also reviewed. Finding 19.
- `callbacks.c`: `DetachThread`, `JniEnvKeyInit`, `GetThreadEnv`, callback setup and connection lifecycle, `BridgeClRumble`, `BridgeClRumbleTriggers`, `BridgeClSetMotionEventState`, `BridgeClSetControllerLED`, `BridgeClSetAdaptiveTriggers`, and logging/error propagation. Input arrays, Java argument widths and thread attachment checked; finding 9.
- `minisdl.c`, `minisdl.h`, `controller_type.h`, `controller_list.h`: `GuessControllerType`, `SDL_IsJoystickXboxOne`, `SDL_IsJoystickXboxOneElite`, `SDL_IsJoystickXboxSeriesX`, `SDL_IsJoystickDualSenseEdge`, all type/VID/PID constants and the complete controller tables. Heuristic hardware identities were not changed speculatively.
- `evdev_reader.c`: `hasRelAxis`, `hasKey`, `outputEvdevData`, `pollThreadFunc`, `precheckDeviceForPolling`, `startPollForDevice`, `enumerateDevices`, `connectSocket`, `main`, linked-list fields and both mutexes. Findings 34–36; packet sizes, socket failures, device closure, native ABI and hotplug paths checked.

## Device-only follow-ups, not counted as fixed

1. Run Xbox, DS4/DualSense/Edge, Switch Pro, 8BitDo and generic-pad matrices over Bluetooth and USB on representative API levels. Validate kernel scan-code quirks, controller association, physical rumble range/reset, adaptive triggers, player LEDs, calibration and battery reporting. Packet fixtures do not certify firmware behavior.
2. Stress Android InputDevice changes, sensor fallback/rotation, pause/resume and unplug during rumble. The Android-12 sensor-manager platform issue remains firmware-dependent. Also verify disconnect-before-first-input behavior: the predeclared initial controller mask may retain a slot until subsequent assignment/reporting; host-visible effect needs a device/host trace.
3. Two-finger relative tap/scroll handoff remains suspect when the primary finger lifts first: index-based context promotion may lose the secondary tap or produce an unexpected click after scrolling. Capture real pointer sequences and confirm intended gesture behavior before changing the recognizer. This hunt does not claim that handoff fixed.
4. Exercise direct plus sliding OSC touches, custom layouts after fold/unfold/rotation, cutouts, density changes and forced RTL. Saved OSC positions are pixels; migrating the layout format was deliberately not attempted without display evidence. Locale-independent mapping keys and Unicode transport were checked separately.
5. Test 120/144 Hz+, VRR, high-poll-rate mice and simultaneous USB sensors/controllers for queue latency. Input math/timers are not tied to display FPS, but main-thread scheduling and vendor capture behavior need measurement. Check DeX/ChromeOS/S Pen capture and pen contact ellipse geometry with real digitizer traces.
6. On rooted API 21–25 devices, test SU grant/deny/dismiss, immediate stream exit during startup, hotplug, grab/ungrab and 32/64-bit event layouts. The source fixes and four ABI builds do not certify vendor SU/kernel behavior.
7. Exercise native reconnect and full-input-queue failure with sanitizers/fault injection, and verify emoji/CJK/dead-key/IME composition end to end against the host. No host code or host configuration was changed.

## Native patch preservation

`app/src/main/jni/moonlight-core/moonlight-common-c` contains the supplied sources but has no `.git` metadata in this worktree. Its parent entry is a gitlink, so ordinary parent `git diff` does not show the applied `InputStream.c` changes. The source is edited in place and was compiled successfully. The patch generated with `git diff --no-index` against the captured original is preserved below and in `build/bughunt/native-input.patch`; retain it when transferring these uncommitted changes. No source directory blocked editing.

```diff
diff --git a/app/src/main/jni/moonlight-core/moonlight-common-c/src/InputStream.c b/app/src/main/jni/moonlight-core/moonlight-common-c/src/InputStream.c
index 59948364..8b6f8d88 100644
--- a/app/src/main/jni/moonlight-core/moonlight-common-c/src/InputStream.c
+++ b/app/src/main/jni/moonlight-core/moonlight-common-c/src/InputStream.c
@@ -119,6 +119,7 @@ int initializeInputStream(void) {
     absCurrentPosX = absCurrentPosY = 0.5f;
 
     memset(currentGamepadSensorState, 0, sizeof(currentGamepadSensorState));
+    memset(currentQueuedControllerPacket, 0, sizeof(currentQueuedControllerPacket));
     memset(&currentRelativeMouseState, 0, sizeof(currentRelativeMouseState));
     memset(&currentAbsoluteMouseState, 0, sizeof(currentAbsoluteMouseState));
     PltCreateMutex(&batchedInputMutex);
@@ -840,8 +841,8 @@ int LiSendMousePositionEvent(short x, short y, short referenceWidth, short refer
 // Send a relative motion event using absolute position to the streaming machine
 int LiSendMouseMoveAsMousePositionEvent(short deltaX, short deltaY, short referenceWidth, short referenceHeight) {
     // Convert the current position to be relative to the provided reference dimensions
-    short oldPositionX = (short)(absCurrentPosX * referenceWidth);
-    short oldPositionY = (short)(absCurrentPosY * referenceHeight);
+    short oldPositionX = (short)(absCurrentPosX * (referenceWidth - 1) + 0.5f);
+    short oldPositionY = (short)(absCurrentPosY * (referenceHeight - 1) + 0.5f);
 
     return LiSendMousePositionEvent(CLAMP(oldPositionX + deltaX, 0, referenceWidth),
                                     CLAMP(oldPositionY + deltaY, 0, referenceHeight),
@@ -1150,6 +1151,11 @@ static int sendControllerEventInternal(short controllerNumber, short activeGamep
         if (err != LBQ_SUCCESS) {
             LC_ASSERT(err == LBQ_BOUND_EXCEEDED);
             Limelog("Input queue reached maximum size limit\n");
+            PltLockMutex(&batchedInputMutex);
+            if (currentQueuedControllerPacket[controllerNumber] == holder) {
+                currentQueuedControllerPacket[controllerNumber] = NULL;
+            }
+            PltUnlockMutex(&batchedInputMutex);
             freePacketHolder(holder);
         }
     }
@@ -1533,8 +1539,8 @@ int LiSendControllerMotionEvent(uint8_t controllerNumber, uint8_t motionType, fl
     }
 
     // Check for valid motion type values
-    if (motionType - 1 >= MAX_MOTION_EVENTS) {
-        LC_ASSERT(motionType - 1 < MAX_MOTION_EVENTS);
+    if (motionType < 1 || motionType > MAX_MOTION_EVENTS) {
+        LC_ASSERT(motionType >= 1 && motionType <= MAX_MOTION_EVENTS);
         return -3;
     }
```
