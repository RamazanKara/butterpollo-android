"""Run after assembleNonRootDebug; needs Python 3 and the android-35 sunset AVD.

Uses a disposable, read-only AVD session and a loopback serverinfo fixture.
This checks UI rendering and manual discovery, not pairing or live streaming.
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
    adb("shell", "uiautomator", "dump", "/sdcard/butterpollo-smoke.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/butterpollo-smoke.xml"))


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
            if label in (node.get("text"), node.get("content-desc"), node.get("resource-id")):
                return node
        if scroll:
            area = next((n for n in root.iter("node") if n.get("scrollable") == "true"), None)
            if area is not None:
                x1, y1, x2, y2 = bounds(area)
                adb("shell", "input", "swipe", str((x1+x2)//2), str(y1+(y2-y1)*3//4),
                    str((x1+x2)//2), str(y1+(y2-y1)//3), "300")
        time.sleep(0.5)
    LOGS.joinpath("missing-node.xml").write_bytes(ET.tostring(root))
    raise AssertionError(f"UI node missing: {label}")


def tap(label, scroll=False):
    x1, y1, x2, y2 = bounds(find(label, scroll))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def open_host_profile():
    x1, y1, x2, y2 = bounds(find(HOST_NAME))
    x, y = str((x1+x2)//2), str((y1+y2)//2)
    adb("shell", "input", "swipe", x, y, x, y, "1000")
    tap("Streaming settings for this PC")
    find(f"{HOST_NAME} streaming settings")


def profile_number(label, value):
    label_bottom = bounds(find(label, scroll=True))[3]
    fields = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"
              and bounds(n)[1] >= label_bottom]
    field = min(fields, key=lambda n: bounds(n)[1])
    x1, y1, x2, y2 = bounds(field)
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    adb("shell", "input", "keyevent", "123", *("67" for _ in range(len(field.get("text", "")))))
    adb("shell", "input", "text", value)
    adb("shell", "input", "keyevent", "4")


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
        </root>'''.encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    devices = subprocess.check_output([str(ADB), "devices"], text=True)
    if "emulator-" in devices:
        raise RuntimeError("Stop the existing emulator before running this single-emulator test")
    apk = ROOT / "app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk"
    if not apk.is_file():
        raise FileNotFoundError("Build :app:assembleNonRootDebug before running the smoke test")
    SHOTS.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    fixture = http.server.ThreadingHTTPServer(("127.0.0.1", 0), HostFixture)
    threading.Thread(target=fixture.serve_forever, daemon=True).start()
    with LOGS.joinpath("emulator.log").open("w") as log:
        process = subprocess.Popen([str(EMULATOR), "-avd", "sunset", "-read-only",
                                    "-no-snapshot", "-no-window", "-no-audio", "-no-boot-anim",
                                    "-gpu", "swiftshader", "-cores", "2", "-memory", "2048",
                                    "-port", "5554"], stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Emulator exited; see app/build/emulator-smoke/emulator.log")
                try:
                    if adb("shell", "getprop", "sys.boot_completed", timeout=5).strip() == "1":
                        break
                except (subprocess.SubprocessError, OSError):
                    pass
                time.sleep(2)
            else:
                raise TimeoutError("Emulator did not boot")
            print("Emulator booted; installing debug APK", flush=True)
            adb("install", "-r", str(apk), timeout=90)
            adb("shell", "pm", "clear", PACKAGE)
            adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
            adb("shell", "svc", "wifi", "disable")
            adb("shell", "svc", "data", "disable")
            adb("reverse", "tcp:47989", f"tcp:{fixture.server_port}")
            adb("logcat", "-c")
            adb("shell", "input", "keyevent", "82")
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.PcView")
            find(f"{PACKAGE}:id/manuallyAddPc")
            screenshot("01-launch")
            tap(f"{PACKAGE}:id/manuallyAddPc")
            tap(f"{PACKAGE}:id/hostTextView")
            adb("shell", "input", "text", "127.0.0.1:47989")
            screenshot("02-manual-host")
            tap(f"{PACKAGE}:id/addPcButton")
            find(HOST_NAME)
            screenshot("03-host-added")
            open_host_profile()
            profile_number("Width (pixels, 64–16384)", "0")
            tap("android:id/button1")
            find(f"{HOST_NAME} streaming settings")
            profile_number("Width (pixels, 64–16384)", "1920")
            profile_number("Height (pixels, 64–16384)", "1080")
            profile_number("Refresh rate (1–1000 Hz, up to two decimals)", "59.94")
            screenshot("09-host-profile")
            tap("android:id/button1")
            profile_xml = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/HostStreamProfiles.xml")
            profile = next(n for n in ET.fromstring(profile_xml)
                           if n.get("name") == "00000000-0000-4000-8000-000000000006")
            assert profile.text.startswith("1920,1080,5994,"), profile.text
            open_host_profile()
            find("59.94")
            find("Prefer YUV 4:4:4", scroll=True)
            screenshot("10-host-codec-profile")
            tap("android:id/button3")
            profile_xml = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/HostStreamProfiles.xml")
            assert not list(ET.fromstring(profile_xml)), "Host profile reset did not clear overrides"
            tap(f"{PACKAGE}:id/settingsButton")
            find("Display")
            screenshot("04-settings")
            find("Android low-latency mode", scroll=True)
            screenshot("05-latency-controls")
            overlay = "Show performance overlay"
            tap(overlay, scroll=True)
            screenshot("06-latency-settings")
            preferences = adb("shell", "run-as", PACKAGE, "cat", f"shared_prefs/{PACKAGE}_preferences.xml")
            assert any(n.get("name") == "checkbox_enable_perf_overlay" and n.get("value") == "true"
                       for n in ET.fromstring(preferences)), "Overlay preference was not enabled"
            tap("Controller buttons", scroll=True)
            find("Waiting for a button press…")
            adb("shell", "input", "gamepad", "keyevent", "KEYCODE_BUTTON_Y")
            find("Y sends…")
            screenshot("11-controller-choose")
            tap("A")
            find("Y sends A")
            screenshot("12-controller-buttons")
            mappings = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/ControllerButtonMaps.xml")
            assert any(n.text == "100:96" for n in ET.fromstring(mappings)), mappings
            adb("shell", "input", "keyevent", "4")
            find(overlay)
            adb("shell", "input", "keyevent", "3")
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.PcView")
            find(overlay)
            find("Butterpollo Android", scroll=True)
            screenshot("09-settings-about")
            summary = next((n.get("text", "") for n in tree().iter("node")
                            if "Moonlight" in n.get("text", "")), "")
            assert "GPL-3.0" in summary, "About entry lost the Moonlight attribution"
            adb("shell", "input", "keyevent", "4")
            find(HOST_NAME)
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.limelight.LatencyOverlaySmokeActivity")
            node = find(f"{PACKAGE}:id/performanceOverlay")
            assert "no data yet" in node.get("text", ""), node.attrib
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
            print(f"PASS: manual discovery, host profile validation/save/reset, settings, controller mapping, overlay and rotation; screenshots: {SHOTS}", flush=True)
        finally:
            try:
                LOGS.joinpath("logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
                LOGS.joinpath("crashes.txt").write_text(adb("logcat", "-b", "crash", "-d"), encoding="utf-8")
            except (subprocess.SubprocessError, OSError):
                pass
            try:
                adb("emu", "kill", timeout=10)
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
