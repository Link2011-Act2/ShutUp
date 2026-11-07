param([string[]]$Tasks=@(':app:assembleDebug',':app:assembleDebugAndroidTest'), [string]$Label='development')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$taskSettings=Join-Path $taskRoot 'settings.gradle.kts'
$taskBytes=[IO.File]::ReadAllBytes($taskSettings)
Push-Location $taskRoot
try {
    Add-Content -LiteralPath $taskSettings '
includeBuild("../goldy") { dependencySubstitution { substitute(module("com.github.Link2011-Act2.Goldy:gold")).using(project(":gold")); substitute(module("com.github.Link2011-Act2.Goldy:elief")).using(project(":elief")) } }'
    & ./gradlew.bat @Tasks --console=plain --no-configuration-cache *> "build/transfer-ux-20261107/$Label.log"
    $taskCode=$LASTEXITCODE
    Get-Content "build/transfer-ux-20261107/$Label.log" -Tail 30
    if($taskCode -ne 0) { throw "Development build failed ($taskCode)." }
} finally {
    [IO.File]::WriteAllBytes($taskSettings,$taskBytes)
    Pop-Location
}
