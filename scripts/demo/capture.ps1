param(
    [string[]]$Shot,
    [switch]$NoHost
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $root 'out/demo/clips'
$package = 'com.butterpollo.client'
$remote = '/sdcard/rubylight-demo'
$shots = @(Get-Content -LiteralPath (Join-Path $root 'docs/demo/shotlist.md') | ForEach-Object {
    if ($_ -match '^\| \d{2}-') {
        $cells = $_.Split('|').Trim()
        [pscustomobject]@{ Id = $cells[1]; Seconds = [int]$cells[2]; Driver = $cells[8] }
    }
})
if ($Shot) {
    foreach ($id in $Shot) {
        if ($id -notin $shots.Id) { throw "Unknown shot: $id. See docs/demo/shotlist.md." }
    }
    $shots = @($shots | Where-Object { $_.Id -in $Shot })
}
if ($NoHost) { $shots = @($shots | Where-Object Driver -eq 'auto') }
if ($shots.Count -eq 0) { throw 'No shots selected.' }

$adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $adb = Join-Path $sdk 'platform-tools/adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) { throw 'Put the installed Android platform-tools on PATH.' }
}

function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell can promote successful native-tool diagnostics to errors.
    $ErrorActionPreference = 'Continue'
    $result = & $adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') failed: $result" }
    return ($result -join "`n")
}

function Get-Ui {
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        try {
            $null = Invoke-Adb @('shell', 'uiautomator', 'dump', "$remote/ui.xml")
            $xml = Invoke-Adb @('shell', 'cat', "$remote/ui.xml")
            $tree = [xml]$xml
            [IO.File]::WriteAllText((Join-Path $output 'last-ui.xml'), $xml)
            return $tree
        } catch {
            if ($attempt -eq 3) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Get-Bounds($Node) {
    return @([regex]::Matches($Node.GetAttribute('bounds'), '\d+') | ForEach-Object { [int]$_.Value })
}

function Find-Ui([string]$Label, [switch]$Scroll) {
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        $tree = Get-Ui
        foreach ($node in $tree.SelectNodes('//node')) {
            if ($Label -in @($node.GetAttribute('text').Split("`n")[0],
                    $node.GetAttribute('content-desc'), $node.GetAttribute('resource-id'))) {
                return $node
            }
        }
        if ($Scroll) {
            $area = $tree.SelectSingleNode('//node[@scrollable="true"]')
            if ($area) {
                $b = Get-Bounds $area
                $x = [int](($b[0] + $b[2]) / 2)
                $y1 = [int]($b[1] + ($b[3] - $b[1]) * 0.8)
                $y2 = [int]($b[1] + ($b[3] - $b[1]) * 0.3)
                $null = Invoke-Adb @('shell', 'input', 'swipe', "$x", "$y1", "$x", "$y2", '350')
            }
        }
        Start-Sleep -Milliseconds 400
    }
    throw "UI label '$Label' missing. Use English; inspect out/demo/clips/last-ui.xml."
}

function Tap-Ui([string]$Label, [switch]$Scroll) {
    $b = Get-Bounds (Find-Ui $Label -Scroll:$Scroll)
    $x = [int](($b[0] + $b[2]) / 2)
    $y = [int](($b[1] + $b[3]) / 2)
    $null = Invoke-Adb @('shell', 'input', 'tap', "$x", "$y")
}

function Open-Shot([string]$Id) {
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $package)
    $null = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$package/com.limelight.PcView")
    $tree = Get-Ui
    $guide = $tree.SelectSingleNode('//node[@text="Connect to your PC"]')
    if ($Id -eq '01-launch') {
        if (-not $guide) { Tap-Ui "${package}:id/helpButton" }
        $null = Find-Ui 'Connect to your PC'
        return
    }
    if ($guide) { Tap-Ui 'android:id/button1' }
    Tap-Ui "${package}:id/settingsButton"
    $null = Find-Ui 'Presets'
    switch ($Id) {
        '08-presets' { return }
        '09-upscaling' {
            Tap-Ui 'Stream'
            Tap-Ui 'Client-side upscaling' -Scroll
            $null = Find-Ui 'Bilinear'
        }
        '10-controller' {
            Tap-Ui 'Controls'
            Tap-Ui 'Controller buttons' -Scroll
            $null = Find-Ui 'Controller buttons'
        }
        '11-touch' {
            Tap-Ui 'Controls'
            $null = Find-Ui 'Show on-screen controls' -Scroll
        }
    }
}

$cues = @{
    '02-discovery' = 'Show the discovered PC, then open Add PC.'
    '03-pairing' = 'Show the PIN prompt. Complete pairing on the host during the take.'
    '04-library' = 'Open the paired PC library and hold on the game grid.'
    '05-stream' = 'Prepare a game tile. Tap it at the ACTION cue and let the stream start.'
    '06-compact' = 'Run a stream with the Compact overlay visible.'
    '07-advanced' = 'Run a stream with Advanced visible; scroll once through its groups.'
    '12-pip' = 'Run a stream with PiP enabled. Press Home at the ACTION cue.'
    '13-performance' = 'Hold a steady live stream with measured FPS, latency and frame loss visible.'
}

$null = New-Item -ItemType Directory -Path $output -Force
if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'Connect and authorize one Android device.' }
if ((Invoke-Adb @('shell', 'pm', 'path', $package)) -notmatch 'package:') {
    throw 'Install the current non-root RUBYLIGHT app before capture.'
}
$null = Invoke-Adb @('shell', 'mkdir', '-p', $remote)
foreach ($item in $shots) {
    Write-Host "$($item.Id): $($item.Seconds) seconds in the edit"
    if ($item.Driver -eq 'auto') {
        Open-Shot $item.Id
    } else {
        Write-Host $cues[$item.Id]
        $null = Read-Host 'Prepare the screen, then press Enter to record'
    }
    $null = Get-Ui
    Copy-Item -LiteralPath (Join-Path $output 'last-ui.xml') -Destination (Join-Path $output "$($item.Id).xml") -Force
    $seconds = $item.Seconds + 2
    $remoteClip = "$remote/$($item.Id).mp4"
    $temporary = Join-Path $output "$($item.Id).capture.mp4"
    $process = Start-Process -FilePath $adb -ArgumentList @('shell', 'screenrecord',
        '--size', '1080x2400', '--bit-rate', '20000000', '--time-limit', "$seconds", $remoteClip) `
        -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $output "$($item.Id).record.log") `
        -RedirectStandardError (Join-Path $output "$($item.Id).record-error.log")
    try {
        Start-Sleep -Seconds 1
        Write-Host "ACTION: $($item.Id) - hold for $($item.Seconds) seconds"
        if (-not $process.WaitForExit(($seconds + 15) * 1000)) { throw 'screenrecord timed out.' }
        if ($process.ExitCode -ne 0) { throw "screenrecord failed. See $($item.Id).record-error.log." }
        $null = Invoke-Adb @('pull', $remoteClip, $temporary)
        if ((Get-Item -LiteralPath $temporary).Length -eq 0) { throw 'The captured clip is empty.' }
        Move-Item -LiteralPath $temporary -Destination (Join-Path $output "$($item.Id).mp4") -Force
        $null = Invoke-Adb @('shell', 'rm', $remoteClip)
    } finally {
        if (-not $process.HasExited) { $process.Kill() }
        $process.Dispose()
    }
}
Write-Host "Clips saved in $output"
