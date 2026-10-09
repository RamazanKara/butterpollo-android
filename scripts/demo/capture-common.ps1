$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$package = 'com.butterpollo.client'
$component = "$package/com.limelight.demo.DemoLauncherActivity"
$remote = '/sdcard/rubylight-demo'
$work = Join-Path $root 'out/demo/capture'
New-Item -ItemType Directory -Force $work | Out-Null
$command = Get-Command adb.exe -ErrorAction SilentlyContinue
$adb = if ($command) { $command.Source } else {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = 'C:/Android/sdk' }
    if (-not (Test-Path "$sdk/platform-tools/adb.exe")) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
    Join-Path $sdk 'platform-tools/adb.exe'
}
if (-not (Test-Path -LiteralPath $adb)) { throw 'Put Android platform-tools on PATH.' }

function Invoke-Adb([string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $result = & $adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') failed: $result" }
    return ($result -join "`n")
}

function Get-Ui {
    $null = Invoke-Adb @('shell', 'uiautomator', 'dump', '--compressed', "$remote/ui.xml")
    $xml = Invoke-Adb @('shell', 'cat', "$remote/ui.xml")
    [IO.File]::WriteAllText((Join-Path $work 'last-ui.xml'), $xml)
    return [xml]$xml
}

function Get-Bounds($Node) {
    return @([regex]::Matches($Node.GetAttribute('bounds'), '\d+') | ForEach-Object { [int]$_.Value })
}

function Find-Ui([string]$Label, [switch]$Scroll) {
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        $tree = Get-Ui
        foreach ($node in $tree.SelectNodes('//node')) {
            $description = $node.GetAttribute('content-desc')
            if ($Label -in @($node.GetAttribute('text').Split("`n")[0], $description, $node.GetAttribute('resource-id')) -or
                $description.StartsWith("$Label,")) {
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
                $null = Invoke-Adb @('shell', 'input', 'swipe', "$x", "$y1", "$x", "$y2", '300')
            }
        }
        Start-Sleep -Milliseconds 400
    }
    throw "UI '$Label' missing; use English and inspect out/demo/capture/last-ui.xml."
}

function Tap-Node($Node) {
    $b = Get-Bounds $Node
    $x = [int](($b[0] + $b[2]) / 2)
    $y = [int](($b[1] + $b[3]) / 2)
    $null = Invoke-Adb @('shell', 'input', 'tap', "$x", "$y")
}

function Tap-Ui([string]$Label, [switch]$Scroll) {
    Tap-Node (Find-Ui $Label -Scroll:$Scroll)
}

function Open-DemoState([string]$State) {
    $launchState = if ($State -eq 'upscaling') { 'settings' } else { $State }
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $package)
    $result = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', $component, '--es', 'state', $launchState)
    if ($result -match 'Error|Exception') { throw "Install the demo debug APK first: $result" }
    switch ($State) {
        'hosts' { $null = Find-Ui 'Living-room PC' }
        'library' { $null = Find-Ui 'Racing Game' }
        'settings' { $null = Find-Ui 'Presets' }
        'controller' { $null = Find-Ui 'Demo wireless controller' }
        'upscaling' {
            Tap-Ui 'Stream'
            Tap-Ui 'Client-side upscaling' -Scroll
            $null = Find-Ui 'FSR 1.0'
        }
        default {
            $null = Find-Ui 'DEMO · local sample · scripted stats'
            Start-Sleep -Seconds 2
        }
    }
}

function Enter-DemoPip {
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        Start-Sleep -Milliseconds 500
        $activity = Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')
        if ($activity -match 'mode=pinned|windowingMode=2|mIsInPipMode=true') { return }
    }
    throw 'PiP did not open. Use an Android 8+ phone/emulator with PiP support enabled.'
}

function Initialize-Capture {
    if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'Connect one authorized device or set ANDROID_SERIAL.' }
    $script:oldSize = Invoke-Adb @('shell', 'wm', 'size')
    $script:oldDensity = Invoke-Adb @('shell', 'wm', 'density')
    $script:oldRotation = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'user_rotation')).Trim()
    $script:oldAutoRotation = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'accelerometer_rotation')).Trim()
    $null = Invoke-Adb @('shell', 'mkdir', '-p', $remote)
    $null = Invoke-Adb @('shell', 'wm', 'size', '1080x2400')
    $null = Invoke-Adb @('shell', 'wm', 'density', '420')
    $null = Invoke-Adb @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    $null = Invoke-Adb @('shell', 'settings', 'put', 'system', 'user_rotation', '0')
}

function Restore-Capture {
    if (-not $script:oldSize) { return }
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $package)
    $size = if ($script:oldSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
    $density = if ($script:oldDensity -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
    $null = Invoke-Adb @('shell', 'wm', 'size', $size)
    $null = Invoke-Adb @('shell', 'wm', 'density', $density)
    foreach ($setting in @(@('user_rotation', $script:oldRotation), @('accelerometer_rotation', $script:oldAutoRotation))) {
        if ($setting[1] -eq 'null') {
            $null = Invoke-Adb @('shell', 'settings', 'delete', 'system', $setting[0])
        } else {
            $null = Invoke-Adb @('shell', 'settings', 'put', 'system', $setting[0], $setting[1])
        }
    }
}
