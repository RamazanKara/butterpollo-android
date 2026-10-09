$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\gradlew.bat --no-daemon --max-workers=2 -PabiFilters=arm64-v8a :app:assembleNonRootRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release APK build failed' }
    & .\gradlew.bat --no-daemon --max-workers=2 :app:bundleNonRootRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release bundle build failed' }

    @(
        'app/build/outputs/apk/nonRoot/release/app-nonRoot-release.apk'
        'app/build/outputs/bundle/nonRootRelease/app-nonRoot-release.aab'
    ) | ForEach-Object {
        $artifact = Get-Item -LiteralPath $_
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_).Hash.ToLowerInvariant()
        Write-Output "$hash  $_ ($($artifact.Length) bytes)"
    }
} finally {
    Pop-Location
}
