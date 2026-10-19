package jp.linkserver.shutter.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.AppUpdateState
import jp.linkserver.shutter.update.AppUpdateViewModel
import jp.linkserver.shutter.update.UpdateDeveloperSettings
import jp.linkserver.shutter.maintain.AutoMaintainState
import jp.linkserver.pyrite.screen.*
import jp.linkserver.pyrite.settings.SettingsGroup
import jp.linkserver.pyrite.settings.SettingsNavigationItem

@Composable
internal fun ShutterApp(shutter: ShutterViewModel, showUninstallReminder: Boolean = false,
    onDismissUninstallReminder: () -> Unit = {}, updates: AppUpdateViewModel? = null,
    onConfirmedRelease: (ReleasePermit) -> Unit = { shutter.release(it) },
    onRelease: () -> Unit) {
    val state by shutter.state.collectAsStateWithLifecycle()
    val autoMaintainState by shutter.autoMaintainState.collectAsStateWithLifecycle()
    val offerPending by shutter.autoMaintainOfferPending.collectAsStateWithLifecycle()
    val updateState = updates?.state?.collectAsStateWithLifecycle()?.value ?: AppUpdateState()
    val developerSettings = updates?.developerSettings?.collectAsStateWithLifecycle()?.value ?: UpdateDeveloperSettings()
    val warningDeveloperSettings by shutter.warningDeveloperSettings.collectAsStateWithLifecycle()
    val galaxySettings by shutter.galaxySettings.collectAsStateWithLifecycle()
    ShutterNavigation(state, shutter::authorize, onRelease,
        shutter::restore, shutter::refresh,
        updateState, { updates?.checkManually() ?: Result.success(null) },
        { updates?.hideBanner() }, { updates?.ignoreVersion(it) }, !showUninstallReminder,
        developerSettings, { updates?.setIgnoreVersionComparison(it) }, { updates?.setIgnoreInterval(it) },
        autoMaintainState, shutter::setAutoMaintainEnabled,
        offerPending && !showUninstallReminder, shutter::respondToAutoMaintainOffer,
        warningDeveloperSettings, shutter::setForceSamsung, shutter::setForceNonMainUser,
        galaxySettings, shutter::setGalaxyAudioPolicy, shutter::setEmulateCsc)
    if (state.releaseConfirmationPending && !state.working && !showUninstallReminder) {
        SamsungUserWarningDialog(shutter::cancelReleaseConfirmation) { remember ->
            shutter.confirmRelease(remember)?.let(onConfirmedRelease)
        }
    }
    if (showUninstallReminder) {
        val context = LocalContext.current
        UninstallReminderDialog(state, onDismissUninstallReminder,
            shutter::restore, { openShizuku(context) },
            shutter::authorize, shutter::refresh)
    }
}

@Composable
internal fun ShutterNavigation(
    state: ShutterUiState, onAuthorize: () -> Unit, onRelease: () -> Unit,
    onRestore: () -> Unit, onRefresh: () -> Unit,
    updateState: AppUpdateState = AppUpdateState(),
    onCheckUpdates: suspend () -> Result<GitHubRelease?> = { Result.success(null) },
    onHideUpdateBanner: () -> Unit = {}, onIgnoreUpdate: (String) -> Unit = {},
    updateAllowed: Boolean = true,
    developerSettings: UpdateDeveloperSettings = UpdateDeveloperSettings(),
    onIgnoreVersionComparisonChange: (Boolean) -> Unit = {},
    onIgnoreIntervalChange: (Boolean) -> Unit = {},
    autoMaintainState: AutoMaintainState = AutoMaintainState(),
    onAutoMaintainChange: (Boolean) -> Unit = {},
    autoMaintainOfferPending: Boolean = false,
    onAutoMaintainOfferChoice: (Boolean) -> Unit = {},
    warningDeveloperSettings: WarningDeveloperSettings = WarningDeveloperSettings(),
    onForceSamsungChange: (Boolean) -> Unit = {},
    onForceNonMainUserChange: (Boolean) -> Unit = {},
    galaxySettings: GalaxyControlSettings = GalaxyControlSettings(),
    onGalaxyAudioPolicyChange: (Boolean) -> Unit = {},
    onEmulateCscChange: (Boolean) -> Unit = {}
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    val context = LocalContext.current
    var showUpdatePrompt by rememberSaveable { mutableStateOf(false) }
    var updateReturnScreen by rememberSaveable { mutableStateOf("home") }
    val notification = updateState.notificationUpdate
    val showMaintainOffer = autoMaintainOfferPending && autoMaintainState.armed && !autoMaintainState.enabled &&
        !state.working && !state.releaseConfirmationPending && (!state.nonMainUserWarning || state.nonMainUserConfirmed)
    val canShowUpdate = updateAllowed && !state.working && !showMaintainOffer && !state.releaseConfirmationPending
    val openUpdate = {
        updateReturnScreen = screen
        screen = "update"
        showUpdatePrompt = false
        onHideUpdateBanner()
    }
    val openPrompt = { onHideUpdateBanner(); showUpdatePrompt = true }
    val back = {
        screen = when (screen) { "update" -> updateReturnScreen; "licenses" -> "about"; "about" -> "settings"; else -> "home" }
    }
    if (showUpdatePrompt && notification != null && canShowUpdate && screen == "home") {
        AppUpdatePrompt(notification, openUpdate,
            { showUpdatePrompt = false; onIgnoreUpdate(notification.tag) }, { showUpdatePrompt = false })
    }
    BackHandler(enabled = screen != "home", onBack = back)
    if (showMaintainOffer) AutoMaintainOfferDialog(onAutoMaintainOfferChoice)
    PyriteScreenTransition(
        targetState = screen,
        direction = { initial, target ->
            if (screenDepth(target) > screenDepth(initial)) PyriteNavigationDirection.Forward
            else PyriteNavigationDirection.Backward
        },
        modifier = Modifier.fillMaxSize()
    ) { targetScreen ->
        when (targetScreen) {
            "settings" -> SettingsScreen(back, { screen = "about" }, developerSettings,
                onIgnoreVersionComparisonChange, onIgnoreIntervalChange,
                autoMaintainState, onAutoMaintainChange,
                warningDeveloperSettings, onForceSamsungChange, onForceNonMainUserChange,
                galaxySettings, onGalaxyAudioPolicyChange, state, onRestore, onEmulateCscChange)
            "about" -> AboutScreen(back, { screen = "licenses" }, onCheckUpdates, openUpdate)
            "update" -> updateState.availableUpdate?.let { UpdateOverviewScreen(it, back) }
                ?: ScreenScaffold(stringResource(R.string.about_check_updates), back) { padding ->
                    Text(stringResource(R.string.about_update_latest), Modifier.padding(padding).padding(20.dp))
                }
            "licenses" -> OssLicensesScreen(back)
            else -> HomeScreen(state, { screen = "settings" }, { openShizuku(context) }, onAuthorize,
                onRelease, onRestore, onRefresh,
                galaxyMode = galaxySettings.available,
                updateAction = { if (notification != null && canShowUpdate) AppUpdateAction(openPrompt) },
                updateBanner = {
                    if (notification != null && updateState.showBanner && canShowUpdate && !showUpdatePrompt) {
                        AppUpdateBanner(notification, openPrompt, onHideUpdateBanner)
                        Spacer(Modifier.height(16.dp))
                    }
                })
        }
    }
}

private fun screenDepth(screen: String): Int = when (screen) {
    "settings" -> 1
    "about" -> 2
    "licenses" -> 3
    "update" -> 4
    else -> 0
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to Shizuku's official setup page.
        }
    }
    openUrl(context, "https://shizuku.rikka.app/")
}

@Composable
internal fun ScreenScaffold(title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    PyriteScreenScaffold(title = title,
        navigationIcon = {
            if (onBack != null) PyriteBackButton(onBack, stringResource(R.string.action_back))
        },
        actions = actions,
        content = content
    )
}

@Composable
internal fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit,
    developerSettings: UpdateDeveloperSettings = UpdateDeveloperSettings(),
    onIgnoreVersionComparisonChange: (Boolean) -> Unit = {},
    onIgnoreIntervalChange: (Boolean) -> Unit = {},
    autoMaintainState: AutoMaintainState = AutoMaintainState(),
    onAutoMaintainChange: (Boolean) -> Unit = {},
    warningDeveloperSettings: WarningDeveloperSettings = WarningDeveloperSettings(),
    onForceSamsungChange: (Boolean) -> Unit = {},
    onForceNonMainUserChange: (Boolean) -> Unit = {},
    galaxySettings: GalaxyControlSettings = GalaxyControlSettings(),
    onGalaxyAudioPolicyChange: (Boolean) -> Unit = {},
    shutterState: ShutterUiState = ShutterUiState(),
    onRestore: () -> Unit = {},
    onEmulateCscChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    ScreenScaffold(stringResource(R.string.settings_title), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            SettingsGroup(title = stringResource(R.string.settings_general)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) item("language") {
                    SettingsNavigationItem(stringResource(R.string.settings_language_title),
                        { openAppLanguageSettings(context) }, summary = stringResource(R.string.settings_language_description))
                }
                item("auto_maintain") { AutoMaintainSettingsSection(autoMaintainState, onAutoMaintainChange) }
            }
            SettingsGroup(title = stringResource(R.string.settings_information)) {
                item("about") {
                    SettingsNavigationItem(stringResource(R.string.settings_about_title), onAbout,
                        summary = stringResource(R.string.settings_about_description))
                }
            }
            if (galaxySettings.available) GalaxyControlSettingsSection(galaxySettings, shutterState.working,
                shutterState.hasChange, shutterState.canRestore, onRestore, onGalaxyAudioPolicyChange)
            if (developerSettings.available) {
                DeveloperUpdateSettingsSection(developerSettings,
                    onIgnoreVersionComparisonChange, onIgnoreIntervalChange,
                    warningDeveloperSettings, onForceSamsungChange, onForceNonMainUserChange, shutterState.working, onEmulateCscChange)
            }
        }
    }
}

private fun openAppLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val packageUri = Uri.fromParts("package", context.packageName, null)
    for (action in listOf(Settings.ACTION_APP_LOCALE_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) {
        try {
            context.startActivity(Intent(action, packageUri))
            if (action != Settings.ACTION_APP_LOCALE_SETTINGS) {
                Toast.makeText(context, R.string.language_settings_unavailable, Toast.LENGTH_LONG).show()
            }
            return
        } catch (_: ActivityNotFoundException) {
            // Some manufacturers omit the direct language activity.
        } catch (_: SecurityException) {
            // Fall back to app information on restricted devices.
        }
    }
    Toast.makeText(context, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
}

internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    }
}

