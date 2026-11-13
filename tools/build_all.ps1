param(
    [string[]]$MinecraftVersions = @(),
    [switch]$DownloadDependencies,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$targets = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle/minecraft-targets.json') -Raw | ConvertFrom-Json
if (-not $MinecraftVersions.Count) { $MinecraftVersions = @($targets.PSObject.Properties.Name) }
foreach ($mc in $MinecraftVersions) {
    if ($mc -notin $targets.PSObject.Properties.Name) { throw "Unsupported Minecraft version: $mc" }
}
if ($Offline -and $DownloadDependencies) { throw 'Offline and DownloadDependencies cannot be combined' }
$output = Join-Path $projectRoot 'build/multiversion'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$manifest = @()
foreach ($mc in ($MinecraftVersions | Select-Object -Unique)) {
    if ($DownloadDependencies) {
        & python (Join-Path $PSScriptRoot 'download_dependencies.py') --minecraft $mc
        if ($LASTEXITCODE) { throw "MTR dependency download failed for $mc" }
    }
    foreach ($loader in @('neoforge', 'fabric')) {
        [string[]]$gradleTasks = if ($loader -eq 'neoforge') { @('test', 'testFabricCore', 'jar') } else { @('build') }
        if ($Offline) { $gradleTasks += '--offline' }
        # Use a child process: dev.ps1 deliberately forwards the Gradle exit code with exit.
        & pwsh -NoProfile -File (Join-Path $projectRoot 'dev.ps1') -Platform $loader -MinecraftVersion $mc $gradleTasks
        if ($LASTEXITCODE) { throw "Build failed: Minecraft $mc / $loader" }
        $relativeBuild = if ($mc -eq '1.21.1') {
            if ($loader -eq 'neoforge') { 'build' } else { 'fabric/build' }
        } else { "build/targets/$mc-$loader" }
        $version = ((Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }) -split '=', 2)[1].Trim()
        $source = Join-Path $projectRoot "$relativeBuild/libs/MTR-Navigation-$loader-$version.jar"
        $destination = Join-Path $output "MTR-Navigation-$loader-$version+$mc.jar"
        & python (Join-Path $PSScriptRoot 'verify_build.py') --jar $source --minecraft $mc --loader $loader --version $version
        if ($LASTEXITCODE) { throw "Artifact validation failed: $source" }
        Copy-Item -LiteralPath $source -Destination $destination
        $manifest += [pscustomobject]@{
            minecraft = $mc; loader = $loader; version = $version
            file = [IO.Path]::GetFileName($destination)
            sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding utf8
Write-Output "Validated artifacts: $output"
