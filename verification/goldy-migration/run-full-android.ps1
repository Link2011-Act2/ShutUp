param([Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial, [string]$Adb='adb', [switch]$ProcessStagesOnly)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$taskOutput=Join-Path $taskRoot 'build/full-goldy-20261107'
New-Item -ItemType Directory -Force $taskOutput | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $taskText=(& $Adb -s $Serial @Arguments 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0){throw $taskText}
    return $taskText
}
function Run-Test([string]$Name,[string]$Classes) {
    Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
    $taskResult=Invoke-Adb @('shell','am','instrument','-w','-r','-e','class',$Classes,
        'jp.linkserver.shutter.test/androidx.test.runner.AndroidJUnitRunner')
    $taskResult | Set-Content -Encoding utf8 (Join-Path $taskOutput "$Name.txt")
    if($taskResult -notmatch 'OK \([0-9]+ tests?\)' -or $taskResult -match 'FAILURES|Process crashed|INSTRUMENTATION_FAILED'){throw "Failed $Name"}
    Write-Output "$Name passed"
}
if((Invoke-Adb @('shell','getprop','sys.boot_completed')).Trim() -ne '1'){throw 'Emulator not booted'}
Invoke-Adb @('install','-r','-t',(Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk')) | Out-Null
Invoke-Adb @('install','-r','-t',(Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) | Out-Null
# Changing REQUEST_INSTALL_PACKAGES can terminate the app. Set modes outside instrumentation.
Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
if(-not $ProcessStagesOnly) {
Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','deny') | Out-Null
Run-Test 'permission-denied' 'jp.linkserver.shutter.update.GoldyTransferIntegrationTest#permissionDenialRequiresExplicitRetryAndSettingsReturnNeverLaunchesInstaller'
Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
$taskTransfer='jp.linkserver.shutter.update.GoldyTransferIntegrationTest#'
$taskAllowed=@('handoffOccursOnceAndLeaseSurvivesViewModelRestorationUntilExplicitRelease',
    'permissionGrantAfterProcessRestartRequiresExplicitRetry','cancellationCleansPartialBytesAndRetryStartsFresh',
    'sizeDigestPackageAndVersionMismatchNeverReachInstaller','validSamePackageApkWithForeignSignatureIsRejected',
    'modifiedLeaseIsRejectedOnExplicitReopen') | ForEach-Object { $taskTransfer+$_ }
Run-Test 'transfer-allowed' (($taskAllowed + 'jp.linkserver.shutter.update.UpdatePreferencesMigrationTest') -join ',')
Run-Test 'ui-final' 'jp.linkserver.shutter.ui.AppUpdateUiTest,jp.linkserver.shutter.ui.DeveloperUpdateSettingsTest,jp.linkserver.shutter.ui.GoldyMigrationUiTest,jp.linkserver.shutter.ui.GoldyActivityRestorationTest'
}
Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
foreach($taskStage in @('stage1SeedLegacyPreferences','stage2RestoreLegacyPreferences','stage3RestoreGoldCacheWithoutNetwork')) {
    Run-Test "cache-$taskStage" "jp.linkserver.shutter.ui.GoldyProcessRestorationTest#$taskStage"
}
foreach($taskStage in @('stage1PrepareDurableLeasesAndSimulateHandoffGap','stage2RestoreGapWithoutAutomaticInstallerThenExplicitlyReopen','stage3RestoreHandedOffAndReleaseOnlyExplicitly')) {
    Run-Test "transfer-$taskStage" "jp.linkserver.shutter.update.GoldyTransferProcessTest#$taskStage"
}
