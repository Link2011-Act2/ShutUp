param(
    [Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [string]$Adb='adb',
    [ValidateSet('normal','narrow','activities-dark')][string]$Phase='normal',
    [string]$Classes,
    [string]$RunName='matrix'
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$taskOut=Join-Path $taskRoot 'build/theme-unification-20261107'
function Invoke-Adb([string[]]$Arguments) {
    $taskText=(& $Adb -s $Serial @Arguments 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0){throw $taskText}
    return $taskText
}
if((Invoke-Adb @('shell','getprop','sys.boot_completed')).Trim() -ne '1'){throw 'Emulator not booted'}
Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
Invoke-Adb @('install','-r','-t',(Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk')) | Out-Null
Invoke-Adb @('install','-r','-t',(Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) | Out-Null
if($Phase -eq 'narrow') {
    Invoke-Adb @('shell','wm','size','960x2160') | Out-Null
    Invoke-Adb @('shell','wm','density','480') | Out-Null
    Invoke-Adb @('shell','settings','put','system','font_scale','1.8') | Out-Null
} else {
    Invoke-Adb @('shell','wm','size','reset') | Out-Null
    Invoke-Adb @('shell','wm','density','reset') | Out-Null
    Invoke-Adb @('shell','settings','put','system','font_scale','1.0') | Out-Null
}
Invoke-Adb @('shell','cmd','uimode','night',$(if($Phase -eq 'activities-dark'){'yes'}else{'no'})) | Out-Null
if(-not $Classes) {
    $taskNames=if($Phase -eq 'activities-dark'){@('PyriteActivityRestorationTest','GoldyActivityRestorationTest')}else{
        @('PyritePaletteTest','EliefThemeParityTest','AppUpdateUiTest','GoldyMigrationUiTest','PyriteActivityRestorationTest','GoldyActivityRestorationTest')
    }
    $Classes=($taskNames | ForEach-Object { 'jp.linkserver.shutter.ui.'+$_ }) -join ','
}
$taskResult=Invoke-Adb @('shell','am','instrument','-w','-r','-e','palettePhase',$Phase,'-e','class',$Classes,
    'jp.linkserver.shutter.test/androidx.test.runner.AndroidJUnitRunner')
$taskResult | Set-Content -Encoding utf8 (Join-Path $taskOut "$Phase-$RunName.txt")
if($taskResult -notmatch 'OK \([0-9]+ tests?\)' -or $taskResult -match 'FAILURES|Process crashed|INSTRUMENTATION_FAILED'){
    throw "Failed $Phase/$RunName; see raw instrumentation output"
}
"Passed $Phase/$RunName"
