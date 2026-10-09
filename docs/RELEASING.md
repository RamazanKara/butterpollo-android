# Releasing Butterpollo Android

Butterpollo uses its own semantic version: `0.4.0`, with Android `versionCode`
`316`. Update both in `app/build.gradle` for each release, increasing the code
for every upload (including prereleases). Settings → App and about displays
`BuildConfig.VERSION_NAME`, alongside the Moonlight attribution.

## Signing

Create `keystore.properties` in the repository root, using your existing release
keystore. This file and `*.jks`/`*.keystore` files are ignored by Git.

```properties
storeFile=C:/path/to/butterpollo.jks
storePassword=<keystore password>
keyAlias=<key alias>
keyPassword=<key password>
```

Use forward slashes for Windows paths. Relative paths are resolved from the
repository root. Alternatively, supply `BP_KEYSTORE`, `BP_KEYSTORE_PASSWORD`,
`BP_KEY_ALIAS`, and `BP_KEY_PASSWORD` in the environment. Each nonempty environment
variable overrides its corresponding property. Keep credentials outside scripts,
command history, screenshots, and release attachments.

With no signing values, Gradle prints a warning and signs the release APK and AAB
with the Android debug key. This supports installation and R8 testing; do not
publish those artifacts. Partial signing configuration fails the build. Production
updates must use the same signing identity as previous releases (or the registered
upload key for Play App Signing).

## Build

Initialize native sources with `git submodule update --init --recursive`. Use JDK
17, the SDK/NDK versions pinned in Gradle, and installed Android SDK licenses.
On Windows:

```powershell
$env:JAVA_HOME = 'C:\jdk17'
$env:ANDROID_HOME = 'C:\Android\sdk'
.\scripts\build-release.ps1
```

On WSL, use a Linux JDK 17 and Linux Android SDK/NDK, set `JAVA_HOME` and
`ANDROID_HOME`, then run `bash scripts/build-release.sh`.

Both scripts build an arm64 APK and an AAB containing all supported ABIs, with R8
enabled. They stop on build errors and print SHA-256 hashes and byte sizes for:

- `app/build/outputs/apk/nonRoot/release/app-nonRoot-release.apk`
- `app/build/outputs/bundle/nonRootRelease/app-nonRoot-release.aab`

Retain `app/build/outputs/mapping/nonRootRelease/mapping.txt` and native debug
symbols with each release for crash symbolication. Publishing is a separate step
on the laptop; the scripts do not invoke Git or `gh`.

## Verify

Run the full checks before the artifact script. An unfiltered `assemble` creates
a universal APK, including x86_64 for the emulator; the artifact script replaces
it with the smaller arm64 APK.

```powershell
.\gradlew.bat --no-daemon --max-workers=2 :app:assembleNonRootDebug :app:assembleNonRootRelease :app:bundleNonRootRelease :app:lintNonRootRelease :app:testNonRootDebugUnitTest
```

For the release smoke test, leave signing unconfigured so it uses the debug key,
then run:

```powershell
python scripts/emulator-smoke.py --apk app/build/outputs/apk/nonRoot/release/app-nonRoot-release.apk
```

The script starts and stops a disposable, read-only `sunset` Android 35 Google
APIs AVD. It waits for other emulators to stop. The AVD must support `adb root`:
release APKs are not debuggable, so preference assertions cannot use `run-as`.
Google Play AVD images do not support this. Use the universal APK, not the arm64
download APK, on an x86_64 AVD.

The checks cover launch, adding a fixture host, settings and About version,
preference persistence, controller mapping, PyroWave readiness, frontend errors,
overlay controls, rotation and the app crash buffer. Release screenshots go to
`docs/screenshots/release/` and logs to `app/build/emulator-smoke/release/`.
The debug-only overlay rendering fixture is absent from release APKs: verify the
rendered overlay during a real paired stream, including H.264 and PyroWave on a
supported Vulkan device. A successful fixture smoke test does not verify pairing,
streaming, Shield hardware, or native rendering on a 16 KB device.

## 16 KB native alignment

Check the final signed APK with SDK Build Tools 35 or newer:

```powershell
& "$env:ANDROID_HOME/build-tools/37.0.0/zipalign.exe" -c -P 16 -v 4 app/build/outputs/apk/nonRoot/release/app-nonRoot-release.apk
```

Extract every `lib/**/*.so` from the APK and `base/lib/**/*.so` from the AAB to a
temporary directory. Run the NDK's
`toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe -lW <library.so>`
on each file. Every `LOAD` alignment must be at least `0x4000`; its file offset
and virtual address must match modulo `0x4000`. Check the prebuilt
`libpyrowave-shared.so` as well as `libpyrowave-renderer.so` and
`libmoonlight-core.so`. Vulkan itself is loaded from Android with `dlopen`, not
bundled. Prebuilt ELF alignment cannot be repaired by `zipalign`.

Use `bundletool dump config --bundle=<release.aab>` to confirm
`PAGE_ALIGNMENT_16K`, then validate generated APKs with `zipalign` too. Finally,
test on a 16 KB Android device (`adb shell getconf PAGE_SIZE` must print `16384`).
See the [Android 16 KB page-size guide](https://developer.android.com/guide/practices/page-sizes).
