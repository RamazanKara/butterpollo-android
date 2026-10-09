param([string[]]$Shot)
. (Join-Path $PSScriptRoot 'capture-common.ps1')
$output = Join-Path $root 'out/demo/clips'
New-Item -ItemType Directory -Force $output | Out-Null
$shots = @(Get-Content (Join-Path $root 'docs/demo/shotlist.md') | ForEach-Object {
    if ($_ -match '^\| \d{2}-') {
        $cells = $_.Split('|').Trim()
        [pscustomobject]@{ Id = $cells[1]; Seconds = [int]$cells[2]; State = $cells[8] }
    }
})
if ($Shot) {
    foreach ($id in $Shot) {
        if ($id -notin $shots.Id) { throw "Unknown shot: $id" }
    }
    $shots = @($shots | Where-Object { $_.Id -in $Shot })
}
try {
    Initialize-Capture
    foreach ($shot in $shots) {
        $node = $null
        if ($shot.Id -eq '04-library') {
            Open-DemoState 'hosts'
            $node = Find-Ui 'Living-room PC'
        } elseif ($shot.Id -eq '05-stream') {
            Open-DemoState 'library'
            $node = Find-Ui 'Racing Game'
        } else { Open-DemoState $shot.State }
        $seconds = $shot.Seconds + 2
        $remoteClip = "$remote/$($shot.Id).mp4"
        $recording = Start-Process -FilePath $adb -ArgumentList @('shell', 'screenrecord',
            '--size', '1080x2400', '--bit-rate', '20000000', '--time-limit', "$seconds", $remoteClip) `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output "$($shot.Id).record.log") `
            -RedirectStandardError (Join-Path $output "$($shot.Id).record-error.log")
        try {
            Start-Sleep -Seconds 1
            if ($node) { Tap-Node $node }
            if ($shot.State -eq 'pip') { Enter-DemoPip }
            if (-not $recording.WaitForExit(($seconds + 15) * 1000)) { throw 'screenrecord timed out.' }
            if ($recording.ExitCode -ne 0) { throw "screenrecord failed: $($shot.Id).record-error.log" }
            $temporary = Join-Path $output "$($shot.Id).capture.mp4"
            $null = Invoke-Adb @('pull', $remoteClip, $temporary)
            if ((Get-Item $temporary).Length -eq 0) { throw 'Empty recording.' }
            Move-Item -LiteralPath $temporary -Destination (Join-Path $output "$($shot.Id).mp4") -Force
            $null = Invoke-Adb @('shell', 'rm', $remoteClip)
            Write-Host "Saved $($shot.Id).mp4"
        } finally {
            if (-not $recording.HasExited) { $recording.Kill() }
            $recording.Dispose()
        }
    }
} finally { Restore-Capture }
