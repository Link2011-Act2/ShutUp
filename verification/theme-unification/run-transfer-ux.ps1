param([Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial, [string]$Adb='adb',
    [ValidateSet('integration','permission','background','leases','interruption')][string]$Phase='integration', [string]$Label='development')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$taskOutput=Join-Path $taskRoot 'build/transfer-ux-20261107'
function Invoke-Adb([string[]]$Arguments) {
    $taskText=(& $Adb -s $Serial @Arguments 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0){throw $taskText}
    return $taskText
}
function Run-Test([string]$Name,[string]$Classes) {
    Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
    $taskResult=Invoke-Adb @('shell','am','instrument','-w','-r','-e','class',$Classes,
        'jp.linkserver.shutter.test/androidx.test.runner.AndroidJUnitRunner')
    $taskResult | Set-Content -Encoding utf8 (Join-Path $taskOutput "$Label-$Name.txt")
    if($taskResult -notmatch 'OK \([0-9]+ tests?\)' -or $taskResult -match 'FAILURES|Process crashed|INSTRUMENTATION_FAILED'){throw "Failed $Name"}
    Write-Output "$Name passed"
}
Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
$taskApkSnapshot=Join-Path $taskOutput "apks/$Label-$Phase"
New-Item -ItemType Directory -Force $taskApkSnapshot | Out-Null
foreach($taskRelative in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    $taskSource=Join-Path $taskRoot $taskRelative
    $taskHash=(Get-FileHash $taskSource -Algorithm SHA256).Hash
    $taskCopy=Join-Path $taskApkSnapshot (Split-Path $taskSource -Leaf)
    Copy-Item -LiteralPath $taskSource -Destination $taskCopy
    if((Get-FileHash $taskSource -Algorithm SHA256).Hash -ne $taskHash -or (Get-FileHash $taskCopy -Algorithm SHA256).Hash -ne $taskHash) {
        throw 'APK output changed during snapshot. Wait until the build finishes before running tests.'
    }
    Invoke-Adb @('install','-r','-t',$taskCopy) | Out-Null
}
if([int](Invoke-Adb @('shell','getprop','ro.build.version.sdk')).Trim() -ge 33) {
    Invoke-Adb @('shell','pm','grant','jp.linkserver.shutter','android.permission.POST_NOTIFICATIONS') | Out-Null
}
if($Phase -eq 'permission') {
    $taskStages=@('stage1DeniedDoubleSettingsPersistsIntent','stage2DeniedRestartAndCancelledSettingsRemainSingleAction',
        'stage3GrantedRestartOffersDownloadWithoutReopeningSettings','stage4PreparedLeaseSurvivesPermissionRevocation',
        'stage5GrantedPreparedLeaseOffersOneExplicitInstall')
    for($taskIndex=0;$taskIndex -lt $taskStages.Length;$taskIndex++) {
        $taskMode=if($taskIndex -in @(0,1,3)){'deny'}else{'allow'}
        Invoke-Adb @('shell','am','force-stop','jp.linkserver.shutter') | Out-Null
        Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES',$taskMode) | Out-Null
        Run-Test $taskStages[$taskIndex] ('jp.linkserver.shutter.update.UpdatePermissionRecoveryTest#'+$taskStages[$taskIndex])
    }
} elseif($Phase -eq 'leases') {
    Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
    foreach($taskStage in @('stage1PrepareDurableLeasesAndSimulateHandoffGap','stage2RestoreGapWithoutAutomaticInstallerThenExplicitlyReopen','stage3RestoreHandedOffAndReleaseOnlyExplicitly')) {
        Run-Test $taskStage ('jp.linkserver.shutter.update.GoldyTransferProcessTest#'+$taskStage)
    }
} elseif($Phase -eq 'interruption') {
    Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
    foreach($taskStage in @('stage1StartRealAndroidTransferBeforeProcessDeath','stage2RestorePausedMetadataAndExplicitlyRestartFullValidation')) {
        Run-Test $taskStage ('jp.linkserver.shutter.update.UpdateTransferProcessRecoveryTest#'+$taskStage)
    }
} elseif($Phase -eq 'background') {
    Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
    Run-Test 'background' 'jp.linkserver.shutter.update.UpdateBackgroundExecutionTest'
} else {
    Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','deny') | Out-Null
    Run-Test 'permission-denied' 'jp.linkserver.shutter.update.GoldyTransferIntegrationTest#permissionDenialRequiresExplicitRetryAndSettingsReturnNeverLaunchesInstaller'
    Invoke-Adb @('shell','appops','set','jp.linkserver.shutter','REQUEST_INSTALL_PACKAGES','allow') | Out-Null
    $taskPrefix='jp.linkserver.shutter.update.GoldyTransferIntegrationTest#'
    $taskCases=@('handoffOccursOnceAndLeaseSurvivesViewModelRestorationUntilExplicitRelease','permissionGrantAfterProcessRestartRequiresExplicitRetry',
        'cancellationCleansPartialBytesAndRetryStartsFresh','sizeDigestPackageAndVersionMismatchNeverReachInstaller',
        'validSamePackageApkWithForeignSignatureIsRejected','modifiedLeaseIsRejectedOnExplicitReopen',
        'preparedOlderApkRemainsRetainedWhenNewReleaseIsSelected') | ForEach-Object {$taskPrefix+$_}
    Run-Test 'integration' (($taskCases+'jp.linkserver.shutter.update.UpdatePreferencesMigrationTest') -join ',')
}
