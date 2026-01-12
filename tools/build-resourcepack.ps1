param(
    [string]$OutFile = "cloudframe-resourcepack.zip",
    [int]$PackFormat = 18,
    [string]$Description = "CloudFrame resources"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$assetsDir = Join-Path $repoRoot "cloudframe-assets\src\main\resources\assets"

if (-not (Test-Path $assetsDir)) {
    throw "Assets directory not found: $assetsDir"
}

$tempRoot = Join-Path $env:TEMP ("cloudframe-resourcepack-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

try {
    $packMcmeta = @{
        pack = @{
            pack_format = $PackFormat
            description = $Description
        }
    } | ConvertTo-Json -Depth 4

    Set-Content -Path (Join-Path $tempRoot "pack.mcmeta") -Value $packMcmeta -Encoding UTF8

    Copy-Item -Recurse -Force -Path $assetsDir -Destination (Join-Path $tempRoot "assets")

    $dest = Join-Path $repoRoot $OutFile
    if (Test-Path $dest) {
        Remove-Item -Force $dest
    }

    Compress-Archive -Path (Join-Path $tempRoot "*") -DestinationPath $dest
    Write-Host "Wrote resource pack: $dest"
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -Recurse -Force $tempRoot
    }
}
