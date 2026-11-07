<#
.SYNOPSIS
    Downloads the SIL Open Font License text for every font bundled in res/font.

.DESCRIPTION
    The app ships eight typefaces from Google Fonts. All are OFL-1.1, which
    requires the licence to travel with the font. Only Darumadrop One's text is
    currently checked in, so this script fetches the rest from the google/fonts
    repository into app/src/main/assets/font-licences/ for committing.

    Run once, commit the output, and this script is then historical.

.EXAMPLE
    ./scripts/fetch-font-licences.ps1
#>

$ErrorActionPreference = 'Stop'

$outDir = Join-Path $PSScriptRoot '..\app\src\main\assets\font-licences'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# font resource name -> path inside the google/fonts repository
$fonts = [ordered]@{
    'CharisSIL'   = 'ofl/charissil'
    'Coiny'       = 'ofl/coiny'
    'Enriqueta'   = 'ofl/enriqueta'
    'HennyPenny'  = 'ofl/hennypenny'
    'Lacquer'     = 'ofl/lacquer'
    'Limelight'   = 'ofl/limelight'
}

$base = 'https://raw.githubusercontent.com/google/fonts/main'

foreach ($name in $fonts.Keys) {
    $url = "$base/$($fonts[$name])/OFL.txt"
    $dest = Join-Path $outDir "OFL_$name.txt"
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
        Write-Host "fetched  $name -> OFL_$name.txt"
    }
    catch {
        Write-Warning "failed   $name ($url): $($_.Exception.Message)"
    }
}

Write-Host "`nDone. Commit the contents of app/src/main/assets/font-licences/."
