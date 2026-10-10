param([string]$Banner, [string]$Output)

# Cuts the README banner's ruby "R" tile (docs/assets/banner.png, 1280x400, tile at x 125..286, y 119..280) out as a
# transparent PNG for the video's title cards: the "R" glyph comes from the banner pixels, the flat ruby around it and
# the rounded corners (radius 36 at 162 px, measured from the banner) are rebuilt so no dark banner background
# leaks into the edges.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$src = [Drawing.Bitmap]::FromFile($Banner)
$x0 = 125; $y0 = 119; $size = 162; $radius = 36.0
$ruby = [Drawing.Color]::FromArgb(255, 196, 18, 66)
$tile = New-Object Drawing.Bitmap($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Inside([double]$X, [double]$Y) {
    # Point-in-rounded-rectangle test (rectangle 0..size, corner radius 36).
    $cx = [Math]::Min([Math]::Max($X, $radius), $size - $radius)
    $cy = [Math]::Min([Math]::Max($Y, $radius), $size - $radius)
    return (($X - $cx) * ($X - $cx) + ($Y - $cy) * ($Y - $cy)) -le ($radius * $radius)
}

for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $covered = 0
        foreach ($sy in 0.125, 0.375, 0.625, 0.875) {
            foreach ($sx in 0.125, 0.375, 0.625, 0.875) {
                if (Inside ($x + $sx) ($y + $sy)) { $covered++ }
            }
        }
        if ($covered -eq 0) { $tile.SetPixel($x, $y, [Drawing.Color]::FromArgb(0, 0, 0, 0)); continue }
        $glyph = $x -ge 30 -and $x -lt 132 -and $y -ge 30 -and $y -lt 132
        $c = $(if ($glyph) { $src.GetPixel($x0 + $x, $y0 + $y) } else { $ruby })
        $tile.SetPixel($x, $y, [Drawing.Color]::FromArgb([int][Math]::Round($covered * 255 / 16), $c.R, $c.G, $c.B))
    }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$tile.Save($Output, [Drawing.Imaging.ImageFormat]::Png)
$tile.Dispose(); $src.Dispose()
"saved $Output"
