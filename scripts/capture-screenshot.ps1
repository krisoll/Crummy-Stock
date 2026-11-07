<#
.SYNOPSIS
    Captures the connected device's screen to a real PNG file.

.DESCRIPTION
    `adb exec-out screencap -p > file.png` looks like it works in PowerShell but
    silently corrupts the output: the `>` redirection operator decodes the byte
    stream as text and re-encodes it as UTF-16, so the file starts with a
    byte-order mark (FF FE) instead of the PNG signature (89 50 4E 47). The
    result is a file that image viewers refuse to open.

    This reads the process's raw stdout stream instead, which never passes
    through PowerShell's text encoding layer.

.EXAMPLE
    ./scripts/capture-screenshot.ps1 -Path docs/screenshots/inventory.png
#>

param(
    [Parameter(Mandatory = $true)]
    [string] $Path,

    # Defaults to whatever adb finds on PATH.
    [string] $Adb
)

$ErrorActionPreference = 'Stop'

if (-not $Adb) {
    # No `?.` here: this has to run on Windows PowerShell 5.1, which predates
    # null-conditional operators.
    $found = Get-Command adb -ErrorAction SilentlyContinue
    if ($found) {
        $Adb = $found.Source
    } else {
        $Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    }
}
if (-not (Test-Path $Adb)) {
    throw "adb not found. Pass -Adb <path to adb.exe>."
}

$full = [System.IO.Path]::GetFullPath($Path)
$dir = Split-Path $full -Parent
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $Adb
$psi.Arguments = 'exec-out screencap -p'
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$buffer = [System.IO.MemoryStream]::new()
$proc.StandardOutput.BaseStream.CopyTo($buffer)
$proc.WaitForExit()

$bytes = $buffer.ToArray()

# Fail loudly rather than writing a file that will not open.
if ($bytes.Length -lt 8 -or
    $bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50 -or
    $bytes[2] -ne 0x4E -or $bytes[3] -ne 0x47) {
    throw "Capture did not produce a PNG (got $($bytes.Length) bytes). Is a device connected and unlocked?"
}

[System.IO.File]::WriteAllBytes($full, $bytes)
Write-Host "wrote $full ($([math]::Round($bytes.Length / 1KB, 1)) KB)"
