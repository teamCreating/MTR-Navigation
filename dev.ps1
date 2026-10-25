param(
    [ValidateSet('neoforge', 'fabric')][string]$Platform = 'neoforge',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.cache/gradle'
$env:TEMP = $env:TMP = Join-Path $projectRoot '.tmp'
New-Item -ItemType Directory -Force $env:GRADLE_USER_HOME,$env:TEMP | Out-Null
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
}
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$($env:TEMP)"
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
if (-not $GradleArgs) { $GradleArgs = @('build') }
Push-Location $projectRoot
try {
    if ($Platform -eq 'fabric') {
        & "$projectRoot/fabric/gradlew.bat" -p "$projectRoot/fabric" @GradleArgs
    } else {
        & "$projectRoot/gradlew.bat" @GradleArgs
    }
    exit $LASTEXITCODE
} finally { Pop-Location }
