[CmdletBinding()]
param(
    [ValidateSet('core', 'reborn', 'modern', 'chipped', 'absent')]
    [string]$Suite = 'core',
    [Parameter(Mandatory)][string]$Ae2ltJar,
    [Parameter(Mandatory)][string]$ThunderboltJar,
    [string]$MekmmJar,
    [string]$ChiselModsDir,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
function Require-Path([string]$Value, [string]$Name, [string]$Kind) {
    if ([string]::IsNullOrWhiteSpace($Value) -or !(Test-Path -LiteralPath $Value -PathType $Kind)) {
        throw "${Name} must point to an existing $Kind path: $Value"
    }
    return (Resolve-Path -LiteralPath $Value).Path
}
$ae2lt = Require-Path $Ae2ltJar 'Ae2ltJar' 'Leaf'
$thunderbolt = Require-Path $ThunderboltJar 'ThunderboltJar' 'Leaf'
$previousJavaHome = $env:JAVA_HOME
if ($JavaHome) {
    $java = Require-Path $JavaHome 'JavaHome' 'Container'
    $null = Require-Path (Join-Path $java 'bin/java.exe') 'JavaHome/bin/java.exe' 'Leaf'
}
$gradleArgs = @('--no-configuration-cache', "-Pae2lt_jar=$ae2lt",
    '-Pthunderbolt_maven_notation=', "-Pthunderbolt_jar=$thunderbolt")
if ($Suite -eq 'core') {
    $mekmm = Require-Path $MekmmJar 'MekmmJar' 'Leaf'
    $gradleArgs += @('-I', 'scripts/compat-test.init.gradle', "-PppMekmmJar=$mekmm", 'test', 'jar')
} else {
    $gradleArgs += @('-I', 'scripts/chisel-test.init.gradle', "-PppChiselProfile=$Suite")
    if ($Suite -ne 'absent') {
        $mods = Require-Path $ChiselModsDir 'ChiselModsDir' 'Container'
        $gradleArgs += "-PppChiselModsDir=$mods"
    } elseif ($ChiselModsDir) {
        throw 'The absent suite must run without ChiselModsDir.'
    }
}
$gradleArgs += 'runGameTestServer'
$reports = Join-Path $workspace 'build/reports/compatibility'
$null = New-Item -ItemType Directory -Force -Path $reports
$log = Join-Path $reports "$Suite.log"
$summary = Join-Path $reports "$Suite.json"
$started = [DateTimeOffset]::UtcNow
Push-Location $workspace
try {
    if ($JavaHome) { $env:JAVA_HOME = $java }
    Write-Host "Running compatibility suite '$Suite'. Full log: $log"
    & ./gradlew.bat @gradleArgs 2>&1 | Tee-Object -FilePath $log | ForEach-Object {
        if ($_ -match '^> Task|PP_.*PASS|PP_COMPAT_MOD|GAME TESTS COMPLETE|All .*required tests passed|failed at|BUILD (SUCCESSFUL|FAILED)') {
            Write-Host $_
        }
    }
    $resultCode = $LASTEXITCODE
    $contents = Get-Content -LiteralPath $log -Raw
    $passed = [regex]::Match($contents, 'All (\d+) required tests passed')
    $success = $resultCode -eq 0 -and $passed.Success
    [ordered]@{
        suite = $Suite
        status = $(if ($success) { 'passed' } else { 'failed' })
        requiredTestsPassed = $(if ($passed.Success) { [int]$passed.Groups[1].Value } else { 0 })
        exitCode = $resultCode
        startedUtc = $started.ToString('o')
        finishedUtc = [DateTimeOffset]::UtcNow.ToString('o')
        ae2ltJar = $ae2lt
        thunderboltJar = $thunderbolt
        arguments = $gradleArgs
        log = $log
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $summary -Encoding utf8
    if (!$success) { throw "Compatibility suite '$Suite' failed or did not finish any required GameTests. See $log" }
    Write-Host "Result: $summary"
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}
