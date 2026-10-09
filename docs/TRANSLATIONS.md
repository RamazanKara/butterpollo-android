# App translations

Rubylight currently ships **English and German**. English lives in `app/src/main/res/values/`;
German uses matching filenames in `values-de/`, including arrays and plurals. Use informal **du**
in German, with familiar gaming terms such as Bitrate, Controller and FPS. Keep resource names,
format arguments and plural quantities in sync.

`androidResources.localeFilters` packages only `en` and `de`. Both the in-app language picker
(`values/arrays.xml`) and Android's app language picker (`xml/locales_config.xml`) offer these
languages. System default falls back to English when the device language is unsupported.
Stale `values-xx` translations are removed. Add a language in all three places together only when
its translations are complete.

Keep summaries to one short sentence and dialog explanations to three short lines at 393 dp,
default font size. Settings summaries wrap instead of truncating. Put technical explanations in
[TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Checks

Run `gradlew.bat lint test assemble --no-daemon --max-workers=2` with JDK 17 and the Android SDK.
`MissingTranslation` is enabled as an error; all shipped languages must be complete.
The resource configuration test checks that both language pickers offer exactly English and German.

## Emulator smoke and screenshots

The runner forbids starting an emulator or using adb, so screenshots must be captured by the caller.
Use a 393 dp phone at default font size with a reachable, paired Rubylight PC. Install the
non-root debug APK and use **Settings → App → Language** to select each language. Verify that
text wraps without clipping and explanatory dialogs occupy at most three lines. Use a fresh app
installation or clear app data on a disposable emulator to show the first-run guide again.
For the German first-run guide, set the emulator's system language to German before that fresh start.

Save these twelve screenshots under `docs/screenshots/i18n/`:

| Screen | English | German |
| --- | --- | --- |
| First-run guide | `en-first-run.png` | `de-first-run.png` |
| PC list | `en-pc-list.png` | `de-pc-list.png` |
| Settings root | `en-settings.png` | `de-settings.png` |
| Video settings (Stream) | `en-video.png` | `de-video.png` |
| Stream menu during playback | `en-stream-menu.png` | `de-stream-menu.png` |
| Connection test result | `en-connection-test.png` | `de-connection-test.png` |

Caller commands (PowerShell; repeat the capture with the appropriate filename on each screen):

```powershell
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
adb shell wm size 393x852
adb shell wm density 160
adb shell settings put system font_scale 1.0
New-Item -ItemType Directory -Force docs/screenshots/i18n
adb shell screencap -p /sdcard/i18n.png
adb pull /sdcard/i18n.png docs/screenshots/i18n/en-first-run.png
```

Also select System default with an unsupported system language and check the English fallback.
Exercise both a successful and unavailable connection test, confirm the bitrate and reset dialogs,
and check that German numbers and plural game-export messages display correctly.
