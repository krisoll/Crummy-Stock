<#
.SYNOPSIS
    Downscales a screenshot in place to a README-friendly size.

.DESCRIPTION
    Device screenshots come out at the panel's native resolution (1008x2244 on
    the Pixel 8 Pro). At full size they dominate a README page and push all the
    prose below the fold, so they are scaled to 45% before being committed.

    Bicubic resampling with the high-quality pixel offset is used rather than
    the default nearest-neighbour, which turns small text into mud.

.EXAMPLE
    ./scripts/resize-screenshot.ps1 -Path docs/screenshots/inventory.png

.EXAMPLE
    ./scripts/resize-screenshot.ps1 -Path docs/screenshots -All
#>

param(
    [Parameter(Mandatory = $true)]
    [string] $Path,

    # Scale factor. 0.45 takes 1008x2244 to 454x1010.
    [double] $Scale = 0.45,

    # Treat -Path as a directory and resize every PNG in it.
    [switch] $All
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$files = if ($All) {
    Get-ChildItem -Path $Path -Filter *.png
} else {
    Get-Item -Path $Path
}

foreach ($file in $files) {
    $src = [System.Drawing.Image]::FromFile($file.FullName)
    try {
        $w = [int][math]::Round($src.Width * $Scale)
        $h = [int][math]::Round($src.Height * $Scale)

        # Skip files that have already been scaled; resizing twice would
        # compound the quality loss on every run.
        if ($src.Width -le $w) {
            Write-Host "skipped $($file.Name) (already small: $($src.Width)x$($src.Height))"
            continue
        }

        $bmp = [System.Drawing.Bitmap]::new($w, $h)
        try {
            $bmp.SetResolution(96, 96)
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            try {
                $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $g.DrawImage($src, 0, 0, $w, $h)
            } finally { $g.Dispose() }

            # Save to a sibling temp file first. GDI+ holds a lock on the source
            # file for as long as `$src` is open, and saving onto it in place
            # fails with a bare "generic error occurred in GDI+".
            $tmp = "$($file.FullName).tmp"
            $bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $bmp.Dispose() }
    } finally { $src.Dispose() }

    Move-Item -Path $tmp -Destination $file.FullName -Force

    $kb = [math]::Round((Get-Item $file.FullName).Length / 1KB, 1)
    Write-Host "resized $($file.Name) -> ${w}x${h}, $kb KB"
}
