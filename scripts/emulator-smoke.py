"""Run after assembleNonRootDebug; needs Python 3 and the android-35 sunset AVD.

Uses a disposable, read-only AVD session and a loopback serverinfo fixture.
This checks UI rendering, manual discovery and frontend errors, not pairing or live streaming.
"""
import http.server
import io
import os
from pathlib import Path
import re
import struct
import subprocess
import threading
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk"))
ADB = SDK / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
EMULATOR = SDK / "emulator" / ("emulator.exe" if os.name == "nt" else "emulator")
SERIAL = "emulator-5554"
PACKAGE = "com.butterpollo.client"
SHOTS = ROOT / "docs/screenshots"
LOGS = ROOT / "app/build/emulator-smoke"
HOST_NAME = "Butterpollo smoke fixture"


def adb(*args, timeout=30, binary=False):
    result = subprocess.run([str(ADB), "-s", SERIAL, *args], check=True,
                            capture_output=True, timeout=timeout)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def tree():
    for attempt in range(5):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/butterpollo-smoke.xml")
            return ET.fromstring(adb("shell", "cat", "/sdcard/butterpollo-smoke.xml"))
        except subprocess.CalledProcessError:
            if attempt == 4:
                raise
            time.sleep(2)


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def find(label, scroll=False):
    for attempt in range(16 if scroll else 8):
        root = tree()
        if any(n.get("resource-id") == "android:id/immersive_cling_title" for n in root.iter("node")):
            button = next(n for n in root.iter("node") if n.get("resource-id") == "android:id/ok")
            x1, y1, x2, y2 = bounds(button)
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            continue
        for node in root.iter("node"):
            text = node.get("text") or ""
            if label in (text, text.split("\n")[0], node.get("content-desc"), node.get("resource-id")):
                return node
        if scroll:
            area = next((n for n in root.iter("node") if n.get("scrollable") == "true"), None)
            if area is not None:
                x1, y1, x2, y2 = bounds(area)
                start, end = y1+(y2-y1)*3//4, y1+(y2-y1)//3
                if scroll == "up":
                    start, end = end, start
                adb("shell", "input", "swipe", str((x1+x2)//2), str(start), str((x1+x2)//2), str(end), "300")
        time.sleep(0.5)
    LOGS.joinpath("missing-node.xml").write_bytes(ET.tostring(root))
    raise AssertionError(f"UI node missing: {label}")


def tap(label, scroll=False):
    x1, y1, x2, y2 = bounds(find(label, scroll))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def host_menu(item=None):
    x1, y1, x2, y2 = bounds(find(HOST_NAME))
    x, y = str((x1+x2)//2), str((y1+y2)//2)
    adb("shell", "input", "swipe", x, y, x, y, "1000")
    if item is not None:
        tap(item)


def open_host_profile():
    host_menu("Streaming settings for this PC")
    find(f"{HOST_NAME} streaming settings")


def profile_number(label, value):
    # Width and height share a row and are found by their content description
    fields = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"
              and n.get("content-desc") == label]
    if not fields:
        label_bottom = bounds(find(label, scroll=True))[3]
        fields = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"
                  and bounds(n)[1] >= label_bottom]
    field = min(fields, key=lambda n: bounds(n)[1])
    x1, y1, x2, y2 = bounds(field)
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    adb("shell", "input", "keyevent", "123", *("67" for _ in range(len(field.get("text", "")))))
    adb("shell", "input", "text", value)
    adb("shell", "input", "keyevent", "4")


def prefs(name, check, message):
    """SharedPreferences.apply() writes asynchronously, so poll briefly before failing."""
    for _ in range(20):
        try:
            root = ET.fromstring(adb("shell", "run-as", PACKAGE, "cat", f"shared_prefs/{name}.xml"))
            if check(root):
                return root
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(0.5)
    raise AssertionError(message)


def screenshot(name):
    ui = tree()
    png = adb("exec-out", "screencap", "-p", binary=True)
    try:
        # Keep committed screenshots small: half size, 128-color palette
        from PIL import Image
        image = Image.open(io.BytesIO(png))
        image = image.resize((image.width // 2, image.height // 2), Image.LANCZOS)
        image.quantize(128).save(SHOTS.joinpath(name + ".png"), optimize=True)
    except ImportError:
        SHOTS.joinpath(name + ".png").write_bytes(png)
    LOGS.joinpath(name + ".xml").write_bytes(ET.tostring(ui))
    return struct.unpack(">II", png[16:24])


def frontend_entry(name, contents):
    entry = LOGS / f"butterpollo-smoke-{name}.art"
    entry.write_text(contents, encoding="utf-8", newline="\n")
    remote = f"/sdcard/Download/{entry.name}"
    adb("push", str(entry), remote)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", f"file://{remote}")
    for _ in range(30):
        rows = adb("shell", "content", "query", "--uri", "content://media/external/downloads",
                   "--projection", "_id:_display_name")
        match = re.search(rf"\b_id=(\d+), _display_name={re.escape(entry.name)}\r?$", rows, re.MULTILINE)
        if match:
            uri = f"content://media/external/downloads/{match[1]}"
            assert adb("exec-out", "content", "read", "--uri", uri, binary=True) == entry.read_bytes(), \
                f"Content URI does not contain the fixture: {uri}"
            return uri
        time.sleep(1)
    raise AssertionError(f"MediaStore did not index {remote}: {rows}")


class HostFixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?", 1)[0] != "/serverinfo":
            self.send_error(404)
            return
        body = f'''<?xml version="1.0"?><root status_code="200">
          <hostname>{HOST_NAME}</hostname>
          <uniqueid>00000000-0000-4000-8000-000000000006</uniqueid>
          <appversion>7.1.431.-1</appversion><GfeVersion>3.23.0.74</GfeVersion>
          <PairStatus>0</PairStatus><currentgame>0</currentgame>
          <state>SUNSHINE_SERVER_FREE</state><HttpsPort>47984</HttpsPort>
          <ServerCodecModeSupport>197377</ServerCodecModeSupport>
          <RustHostVersion>2.0.0</RustHostVersion>
        </root>'''.encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    apk = ROOT / "app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk"
    if not apk.is_file():
        raise FileNotFoundError("Build :app:assembleNonRootDebug before running the smoke test")
    deadline = time.monotonic() + 30 * 60
    while "emulator-" in subprocess.check_output([str(ADB), "devices"], text=True, timeout=30):
        if time.monotonic() >= deadline:
            raise TimeoutError("Existing emulator is still in use after 30 minutes")
        print("Waiting for the existing emulator to stop", flush=True)
        time.sleep(30)
    SHOTS.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    fixture = http.server.ThreadingHTTPServer(("127.0.0.1", 0), HostFixture)
    threading.Thread(target=fixture.serve_forever, daemon=True).start()
    with LOGS.joinpath("emulator.log").open("w") as log:
        process = subprocess.Popen([str(EMULATOR), "-avd", "sunset", "-read-only",
                                    "-no-snapshot", "-no-window", "-no-audio", "-no-boot-anim",
                                    "-gpu", os.environ.get("BUTTERPOLLO_EMULATOR_GPU", "swiftshader"), "-cores", "2", "-memory", "4096",
                                    "-port", "5554"], stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        try:
            deadline = time.monotonic() + int(os.environ.get("BUTTERPOLLO_BOOT_TIMEOUT", "180"))
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Emulator exited; see app/build/emulator-smoke/emulator.log")
                try:
                    if (adb("shell", "getprop", "sys.boot_completed", timeout=5).strip() == "1" and
                            adb("shell", "pm", "path", "android", timeout=5).startswith("package:")):
                        break
                except (subprocess.SubprocessError, OSError):
                    pass
                time.sleep(2)
            else:
                raise TimeoutError("Emulator boot or package manager did not become ready")
            adb("shell", "input", "keyevent", "82", "3")
            previous = None
            while time.monotonic() < deadline:
                try:
                    launcher = adb("shell", "cmd", "package", "resolve-activity", "--brief",
                                   "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME",
                                   timeout=5).strip().split("\n")[-1]
                    ui = tree()
                    current = ET.tostring(ui)
                    # HOME can still resolve to a temporary boot screen after boot_completed.
                    if ("/" in launcher and not launcher.startswith(("com.android.settings/", "com.google.android.googlesdksetup/")) and
                            any(n.get("package") == launcher.split("/")[0] for n in ui.iter("node"))):
                        if current == previous:
                            break
                        previous = current
                    else:
                        previous = None
                except (subprocess.SubprocessError, ET.ParseError):
                    previous = None
                time.sleep(2)
            else:
                raise TimeoutError("Emulator launcher did not settle")
            print("Emulator services and launcher ready; installing debug APK", flush=True)
            adb("install", "-r", str(apk), timeout=90)
            adb("shell", "pm", "clear", PACKAGE)
            adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
            adb("shell", "svc", "wifi", "disable")
            adb("shell", "svc", "data", "disable")
            adb("reverse", "tcp:47989", f"tcp:{fixture.server_port}")
            adb("logcat", "-c")
            adb("shell", "input", "keyevent", "82")
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.PcView")
            find("Connect to your PC")
            screenshot("01-launch")
            tap("android:id/button1")
            tap(f"{PACKAGE}:id/manuallyAddPc")
            tap(f"{PACKAGE}:id/hostTextView")
            adb("shell", "input", "text", "127.0.0.1:47989")
            screenshot("02-manual-host")
            tap(f"{PACKAGE}:id/addPcButton")
            find(HOST_NAME)
            screenshot("03-host-added")
            host_menu()
            find("Pair with one-time PIN")
            assert not any(n.get("text") == "Add games to ES-DE" for n in tree().iter("node")), \
                "Unpaired PC offers frontend export"
            screenshot("19-unpaired-host-menu")
            tap("Pair with one-time PIN")
            find("One-time PIN")
            screenshot("16-otp-pairing")
            tap("android:id/button2")
            host_menu("View details")
            find("Butterpollo 2.0.0")
            screenshot("15-host-details")
            tap("android:id/button1")
            open_host_profile()
            find("This PC uses your global settings. Save to give it its own.")
            profile_number("Width", "0")
            tap("android:id/button1")
            find(f"{HOST_NAME} streaming settings")
            profile_number("Width", "1920")
            profile_number("Height", "1080")
            profile_number("Refresh rate (Hz)", "59.94")
            profile_number("Bitrate (Mbps)", "45.5")
            screenshot("09-host-profile")
            tap("android:id/button1")
            prefs("HostStreamProfiles", lambda root: any(
                n.get("name") == "00000000-0000-4000-8000-000000000006" and
                (n.text or "").startswith("1920,1080,5994,45500,") for n in root), "Host profile was not saved")
            open_host_profile()
            find("This PC has its own settings. Use global settings to remove them.")
            find("59.94")
            find("Prefer YUV 4:4:4", scroll=True)
            screenshot("10-host-codec-profile")
            tap("android:id/button3")
            prefs("HostStreamProfiles", lambda root: not list(root), "Host profile reset did not clear overrides")
            tap(f"{PACKAGE}:id/settingsButton")
            find("Reset all settings")
            screenshot("04-settings")
            tap("Video and display")
            find("Display")
            screenshot("05-video-settings")
            tap("Video bitrate")
            tap("80")
            tap("Increase")
            find("81 Mbps")
            screenshot("06-bitrate")
            tap("android:id/button1")
            prefs(f"{PACKAGE}_preferences", lambda root: any(
                n.get("name") == "seekbar_bitrate_kbps" and n.get("value") == "81000" for n in root),
                "Bitrate was not stored in kbps")
            tap("Video codec", scroll=True)
            tap("Prefer PyroWave (experimental, high bandwidth)")
            summary = find("Prefer PyroWave (experimental, high bandwidth)", scroll=True)
            assert "\nNot supported: " in summary.get("text", ""), summary.attrib
            screenshot("20-pyrowave-readiness")
            tap("Video codec")
            tap("Automatic (recommended)")
            adb("shell", "input", "keyevent", "4")
            find("1280×720 · 60 FPS · 81 Mbps · Automatic")
            tap("Latency and diagnostics")
            find("Android low-latency mode", scroll=True)
            overlay = "Show performance overlay"
            tap(overlay, scroll=True)
            screenshot("13-latency-settings")
            prefs(f"{PACKAGE}_preferences", lambda root: any(
                n.get("name") == "checkbox_enable_perf_overlay" and n.get("value") == "true" for n in root),
                "Overlay preference was not enabled")
            adb("shell", "input", "keyevent", "3")
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.PcView")
            find(overlay)
            tap("Navigate up")
            tap("App and about")
            find("Butterpollo Android", scroll=True)
            screenshot("14-settings-about")
            summary = next((n.get("text", "") for n in tree().iter("node")
                            if "Moonlight" in n.get("text", "")), "")
            assert "GPL-3.0" in summary, "About entry lost the Moonlight attribution"
            tap("Navigate up")
            tap("Controllers, touch and mouse")
            tap("Controller buttons", scroll=True)
            find("Waiting for a button press…")
            adb("shell", "input", "gamepad", "keyevent", "KEYCODE_BUTTON_Y")
            find("Y sends…")
            screenshot("11-controller-choose")
            tap("A")
            find("Sends A")
            screenshot("12-controller-buttons")
            prefs("ControllerButtonMaps", lambda root: any(n.text == "100:96" for n in root),
                  "Controller mapping was not saved")
            adb("shell", "input", "keyevent", "4")
            find("Controller buttons")
            tap("Navigate up")
            tap("Reset all settings")
            tap("android:id/button1")
            find("1280×720 · 60 FPS · 10 Mbps · Automatic")
            prefs(f"{PACKAGE}_preferences", lambda root: not any(
                n.get("name") == "checkbox_enable_perf_overlay" and n.get("value") == "true" for n in root),
                "Reset kept the overlay setting")
            prefs("ControllerButtonMaps", lambda root: any(n.text == "100:96" for n in root),
                  "Reset removed the controller mapping")
            adb("shell", "input", "keyevent", "4")
            find(HOST_NAME)
            for name, contents, message, shot in (
                    ("malformed", "This is not a game entry.\n",
                     "This game file can't be opened. Add the games again from Butterpollo.", "17-frontend-malformed"),
                    ("unknown-host", "# Butterpollo game entry\n"
                     "[host_uuid] 00000000-0000-4000-8000-000000000007\n"
                     "[host_name] Unknown smoke PC\n[app_uuid] 00000000-0000-4000-8000-000000000008\n"
                     "[app_name] Smoke game\n[app_id] 123\n", "PC not found", "18-frontend-unknown-host")):
                uri = frontend_entry(name, contents)
                adb("shell", "input", "keyevent", "3")
                find(f"{launcher.split('/')[0]}:id/workspace")
                adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.ShortcutTrampoline",
                    "-a", "android.intent.action.VIEW", "-d", uri, "--grant-read-uri-permission")
                find(message)
                if name == "malformed":
                    assert "Unreadable frontend entry: Not a game entry file" in adb("logcat", "-d"), \
                        "Malformed entry was not read by the app"
                screenshot(shot)
                tap("android:id/button1")
                find(HOST_NAME)
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.LatencyOverlaySmokeActivity")
            node = find(f"{PACKAGE}:id/performanceOverlay")
            assert "no data yet" in node.get("text", ""), node.attrib
            assert "Client latency (receive → present): no data yet" in node.get("text", ""), node.attrib
            assert "Decode → present: no data yet" in node.get("text", ""), node.attrib
            width, height = screenshot("07-latency-overlay")
            adb("shell", "wm", "user-rotation", "lock", "1" if width < height else "0")
            time.sleep(1)
            find(f"{PACKAGE}:id/performanceOverlay")
            rotated_width, rotated_height = screenshot("08-overlay-rotated")
            assert (width < height) != (rotated_width < rotated_height), "Overlay did not rotate"
            crashes = adb("logcat", "-b", "crash", "-d")
            LOGS.joinpath("logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
            LOGS.joinpath("crashes.txt").write_text(crashes, encoding="utf-8")
            if "FATAL EXCEPTION" in crashes or "Fatal signal" in crashes:
                raise AssertionError("Emulator crash buffer is not clean")
            print(f"PASS: pairing guide, manual discovery, OTP and details dialogs, host profile validation/save/reset, settings screens, PyroWave readiness, bitrate, controller mapping, reset, frontend entries, unpaired export menu, overlay and rotation; screenshots: {SHOTS}", flush=True)
        finally:
            try:
                LOGS.joinpath("logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
                LOGS.joinpath("crashes.txt").write_text(adb("logcat", "-b", "crash", "-d"), encoding="utf-8")
            except (subprocess.SubprocessError, OSError):
                pass
            try:
                if "OK" not in adb("emu", "kill", timeout=10):
                    adb("shell", "reboot", "-p", timeout=10)
            except (subprocess.SubprocessError, OSError):
                process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
            fixture.shutdown()
            fixture.server_close()


if __name__ == "__main__":
    main()
