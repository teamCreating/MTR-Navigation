param([Parameter(Mandatory = $true)][string]$ProfileRoot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$profile = [IO.Path]::GetFullPath($ProfileRoot).TrimEnd('\')
$mods = [IO.Path]::GetFullPath((Join-Path $profile 'mods'))
if ($mods -ne "$profile\mods" -or -not (Test-Path -LiteralPath $mods -PathType Container)) { throw 'Invalid profile mods directory' }
$source = Join-Path $projectRoot 'build\libs\MTR-Navigation-neoforge-0.1.0.jar'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw 'Build the NeoForge JAR first' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Test-NavigationJar([string]$JarPath) {
    $zip = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $zip.GetEntry('META-INF/neoforge.mods.toml')
        if (-not $entry) { return $false }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() -match '(?m)^\s*modId\s*=\s*"mtrnav"\s*$' } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}
if (-not (Test-NavigationJar $source)) { throw 'Source is not an MTR Navigation NeoForge build' }
$old = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object { Test-NavigationJar $_.FullName })
$backup = Join-Path $projectRoot ('.cache\profile-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
$records = @()
foreach ($jar in $old) {
    $resolved = [IO.Path]::GetFullPath($jar.FullName)
    if ([IO.Path]::GetDirectoryName($resolved) -ne $mods) { throw 'Refusing to move a file outside the selected mods directory' }
    $records += [pscustomobject]@{ original = $resolved; sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash; backup = (Join-Path $backup $jar.Name) }
    Move-Item -LiteralPath $resolved -Destination (Join-Path $backup $jar.Name)
}
$records | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding utf8
$destination = Join-Path $mods ([IO.Path]::GetFileName($source))
Copy-Item -LiteralPath $source -Destination $destination
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) { throw 'Installed JAR checksum mismatch' }
$installed = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object { Test-NavigationJar $_.FullName })
if ($installed.Count -ne 1) { throw 'Expected exactly one Navigation JAR after installation' }
Write-Output "Installed: $destination"
Write-Output "Old builds backed up: $($old.Count), $backup"
Write-Output "Navigation JAR count: $($installed.Count)"
Write-Output "SHA256: $((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash)"
