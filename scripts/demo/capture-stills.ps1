. (Join-Path $PSScriptRoot 'capture-common.ps1')
$output = Join-Path $root 'docs/screenshots/demo'
New-Item -ItemType Directory -Force $output | Out-Null
$states = [ordered]@{
    'pc-list' = 'hosts'; 'library' = 'library'; 'stream' = 'stream';
    'stream-compact' = 'compact'; 'stream-advanced' = 'advanced';
    'settings-presets' = 'settings'; 'upscaling-options' = 'upscaling';
    'controller' = 'controller'; 'touch-controller' = 'touch'; 'pip' = 'pip'
}
try {
    Initialize-Capture
    foreach ($shot in $states.GetEnumerator()) {
        Open-DemoState $shot.Value
        if ($shot.Value -eq 'pip') { Enter-DemoPip }
        Start-Sleep -Milliseconds 800
        $path = Join-Path $output "$($shot.Key).png"
        $null = Invoke-Adb @('shell', 'screencap', '-p', "$remote/still.png")
        $null = Invoke-Adb @('pull', "$remote/still.png", $path)
        $bytes = [IO.File]::ReadAllBytes($path)
        $width = ($bytes[16] * 16777216) + ($bytes[17] * 65536) + ($bytes[18] * 256) + $bytes[19]
        $height = ($bytes[20] * 16777216) + ($bytes[21] * 65536) + ($bytes[22] * 256) + $bytes[23]
        if ($width -ne 1080 -or $height -ne 2400) { throw "$path is ${width}x${height}, expected 1080x2400." }
        Write-Host "Saved $path"
    }
    $null = Invoke-Adb @('shell', 'rm', "$remote/still.png")
} finally { Restore-Capture }
