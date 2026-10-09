# Launching games from ES-DE and other frontends

Rubylight can start a stream straight from ES-DE, Daijisho, Pegasus or any launcher that can send
an Android intent. Each host game becomes a small `.art` file in your ROMs folder; the frontend opens
that file with Rubylight, which wakes the PC if needed, connects and starts the game. When the
stream ends you are back in the frontend.

The file format is the same as Artemis', so entries made by
[ApolloLauncherExport](https://github.com/ClassicOldSong/ApolloLauncherExport) work too.

## ES-DE in two minutes

1. Pair your PC in Rubylight and open its game list once, so the app knows the games.
2. Long-press the PC and choose **Add games to ES-DE**.
3. Pick your ROMs folder (the one ES-DE uses, for example `ROMs` on internal storage or the SD card).
   Rubylight creates `ROMs/butterpollo/` with one `.art` file per game. Hidden games are skipped.
4. Pick your ES-DE folder (usually `ES-DE` on internal storage). Rubylight adds its system to
   `ES-DE/custom_systems/es_systems.xml` and `es_find_rules.xml` and copies the game covers it has
   into `ES-DE/downloaded_media/butterpollo/covers/`. Your other custom systems are kept.
5. Restart ES-DE. A **Rubylight** system appears; it uses the Windows theme art and scrapes as PC games.

Run the export again after adding games on the host. Existing entries are updated in place; if two
PCs have a game with the same name, the second one gets the PC name in brackets.

Folders on Android 11 and later can't be the root of internal storage or `Download`; pick the
`ROMs` and `ES-DE` folders themselves.

### What the export writes

`ES-DE/custom_systems/es_systems.xml` (merged):

```xml
<system>
    <name>butterpollo</name>
    <fullname>Rubylight</fullname>
    <path>%ROMPATH%/butterpollo</path>
    <extension>.art .ART</extension>
    <command label="Rubylight">%EMULATOR_BUTTERPOLLO% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%</command>
    <platform>pc</platform>
    <theme>windows</theme>
</system>
```

`ES-DE/custom_systems/es_find_rules.xml` (merged):

```xml
<emulator name="BUTTERPOLLO">
    <rule type="androidpackage">
        <entry>com.butterpollo.client/com.limelight.ShortcutTrampoline</entry>
    </rule>
</emulator>
```

The root build writes `com.butterpollo.client.root` instead.

`ROMs/butterpollo/Elden Ring.art`:

```
# Rubylight game entry
[host_uuid] 6A1B…
[host_name] Gaming PC
[app_uuid] 3F0C…
[app_name] Elden Ring
[app_id] 1234567
```

The app UUID is what launches the game, so renaming a game on the host or a changed numeric ID
does not break the entry. Entries without `app_uuid` fall back to `app_id`, then to `app_name`.
Without a host UUID, `host_name` must match the PC name shown in Rubylight.

## Daijisho

Add a platform whose player uses:

```
-n com.butterpollo.client/com.limelight.ShortcutTrampoline
 -a android.intent.action.VIEW
 -d {file.uri}
```

with accepted file names `^(.*)\.(?:art)$`, and point the platform at the `ROMs/butterpollo` folder.

## Pegasus

```
launch: am start -n com.butterpollo.client/com.limelight.ShortcutTrampoline -a android.intent.action.VIEW -d {file.uri}
```

## Any other launcher or script

Rubylight also accepts plain extras, like Moonlight:

```sh
adb shell am start -n com.butterpollo.client/com.limelight.ShortcutTrampoline \
  --es UUID <host uuid> --es AppUuid <app uuid>
```

| Extra | Meaning |
| --- | --- |
| `UUID` | Host UUID (Rubylight shows it under **View details** on a PC) |
| `Name` | Host name, used when `UUID` is missing |
| `AppUuid` | App UUID, preferred |
| `AppId` | Numeric app ID |
| `AppName` | App name, looked up in the cached game list |

Without any app extra, Rubylight opens that PC's game list instead.

## Troubleshooting

- **"This game file can't be opened"**: the file isn't a Rubylight or Artemis entry, or the
  frontend didn't grant access to it. Export again and check the ES-DE command uses `%ROMPROVIDER%`.
- **"PC not found"**: the entry was made on another phone or the PC was removed. Pair the PC here and export again.
- **The game list opens instead of the game**: the entry has no app fields. Export again.
- **No Rubylight system in ES-DE**: restart ES-DE after the export and check that
  `ES-DE/custom_systems/` contains both XML files.
