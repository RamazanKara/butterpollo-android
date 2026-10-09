# Rubylight rename audit

Audited after `1029398c` (rename) and `2da0c238` (translations), using a
repository-wide, case-insensitive `git grep -n -i butterpollo` search. Ripgrep
was unavailable. The populated native submodule was also searched separately.
The patch builds on `2da0c238`; an older target branch needs the prerequisite
rename and translation commits first.

## User-facing text

- Changed the ES-DE system fullname and command label to Rubylight in
  `FrontendExporter.java` and `FrontendEntry.java`. Exported `.art` headers and
  the generated emulator-rule comment now use Rubylight too.
- Changed the English and German store release notes in
  `fastlane/metadata/android/{en-US,de}/changelogs/316.txt`.
- Updated the ES-DE troubleshooting text in `FRONTENDS.md` and the host section
  heading in `ROADMAP-LOWLATENCY.md`.
- Checked all `res/values*` directories, generated app labels, manifests,
  overlay/about/problem-report/share text, notification text and shortcut labels,
  store descriptions/titles, README, documentation and scripts' printed text.
  Their remaining matches are identifiers, paths, URLs or historical test data,
  rather than old product labels. English and German resource values were
  already renamed; resource keys stay unchanged.
- Daijisho uses the shared `.art` export and Android activity metadata. There is
  no separate Daijisho platform JSON in this repository. Its setup documentation
  already names Rubylight.

Existing ES-DE exports receive the new display labels when exported again;
restart ES-DE afterwards. Existing game entries and cover folders remain valid.

## Names intentionally retained

| Names and locations | Recommendation |
| --- | --- |
| Application IDs `com.butterpollo.client` and `com.butterpollo.client.root` in `app/build.gradle`; derived `.reports` provider authorities | Keep them permanently for in-place upgrades, app data, pairings and existing Android intents. |
| Java namespace/packages `com.limelight`; `com.butterpollo.` crash-stack matching; `ButterpolloApplication` and all class/file names | Keep unchanged. These are code/component identities, not display labels. |
| Resource keys `category_butterpollo_host`, `host_details_software_butterpollo`, `ThemeOverlay.Butterpollo.Dialog`, `ButterpolloDialogButton`, `ShapeAppearance.Butterpollo`; drawable names `butterpollo_banner`, `ic_butterpollo_foreground` | Keep the existing names and references; their displayed values already use Rubylight. |
| ES-DE system ID `butterpollo`, emulator ID `BUTTERPOLLO`, `%EMULATOR_BUTTERPOLLO%`, `ROMs/butterpollo/` and `downloaded_media/butterpollo/` | Keep stable so re-export replaces the existing system and reuses entries/covers. Change only fullname and command label. Any future ID change needs an explicit migration. |
| `butterpollo-problem.txt`, `butterpollo-latency.csv` and `.tmp`, `butterpollo-smoke.xml`, `butterpollo-smoke-*.art`, host executable `butterpollo.exe`, `docs/BUTTERPOLLO_PARITY.md`, `butterpollo-*.xml` protocol fixtures and the `butterpollo-android/codex-prompts-lowlatency.md` reference | Keep filenames and documented commands aligned. Rename only in a separate, coordinated migration if needed. |
| `butterpollo-unsigned.apk`, `butterpollo-aligned.apk`, `butterpollo.apk`, `butterpollo-arm64-debug.apk`, `butterpollo.keystore`, `butterpollo.jks` and signing alias `butterpollo` | Keep build/signing filenames and examples aligned with existing automation; preserve the signing identity. |
| CI artifact names `butterpollo-debug` and `butterpollo-device-test`; script inputs `BUTTERPOLLO_EMULATOR_GPU` and `BUTTERPOLLO_BOOT_TIMEOUT` | Keep existing automation/download identifiers and the matching README instructions. A future change should update all consumers together. |
| Repository URLs (formerly `RamazanKara/Butterpollo` and `RamazanKara/butterpollo-android`) | Switched to `RamazanKara/Rubylight` and `RamazanKara/rubylight-android` after the repositories were renamed on 2026-10-09. GitHub redirects the old URLs; never create a new repository under an old name. |
| Historical protocol fixture contents, test names and internal source comments | Keep reference data and code history intact; these are not shipped product labels. |

## Wire identifiers intentionally retained

None of these paths needs a product rename. Do not replace protocol identity
strings as part of a display-text pass.

| Identifier and source | Recommendation |
| --- | --- |
| Pairing `devicename` is the URL-encoded Android `Build.MODEL`; HTTP `uniqueid` is loaded from the existing `uniqueid` file (`NvHTTP.java`, `IdentityManager.java`) | Preserve the device identity and stored ID to avoid disturbing host pairings. |
| Client certificate CN `NVIDIA GameStream Client` (`AndroidCryptoProvider.java`); key-manager alias `Limelight-RSA` (`NvHTTP.java`) | Preserve certificate generation, existing certificates and key handling. A cosmetic rename must not regenerate identity material. |
| mDNS service types `_nvstream._tcp.local.` and `_nvstream._tcp` (`JmDNSDiscoveryAgent.java`, `NsdManagerDiscoveryAgent.java`) | Keep the discovery protocol names so existing hosts remain discoverable. |
| HTTP `User-Agent`: no application override in `NvHTTP.java`; requests use the HTTP library's default behavior. RTSP adds no product `User-Agent` in `android_rtsp.c`. | Leave existing header behavior unchanged. Any future branded header should be a separate host-compatibility change. |
| RTSP `X-GS-ClientVersion` and the native protocol headers/attributes (`android_rtsp.c`, `android_sdp.c`, native submodule) | Keep negotiated protocol values unchanged; test host interoperability before any separate protocol change. |

## Verification

PowerShell invocation (JDK 17). The default `C:\.gradle` location is not writable
in this environment, so validation uses workspace-local Java, Gradle and Android
user homes without changing project configuration:

```powershell
$env:JAVA_HOME = 'C:\jdk17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$buildHome = Join-Path (Get-Location) '.gradle\rename-validation'
$env:GRADLE_USER_HOME = $buildHome
$env:ANDROID_USER_HOME = "$buildHome\.android"
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$buildHome"
.\gradlew.bat :app:assembleNonRootDebug :app:testNonRootDebugUnitTest :app:lintNonRootDebug --no-daemon --max-workers=2
```

All three tasks passed with exit code 0 on 2026-10-09 using JDK 17.0.20.1 and
Gradle 9.7.1. The unit-test reports contain 615 tests across 58 suites, with no
failures, errors or skips. Lint reports 0 errors and 197 warnings.

The final case-insensitive search leaves only the exceptions listed above.
Displayed string values in all 37 tracked resource XML files were checked
separately, with zero old-name matches. `git diff --check` passed.
No commit, push, `gh`, device or emulator operation was performed.
