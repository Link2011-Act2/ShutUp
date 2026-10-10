package jp.linkserver.shutter.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R

@Composable
fun ShutterApp(shutter: ShutterViewModel, onRelease: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("home") }
    val holder = rememberSaveableStateHolder()
    val state by shutter.state.collectAsStateWithLifecycle()
    val back = {
        screen = when (screen) { "licenses" -> "about"; "about" -> "settings"; else -> "home" }
    }
    BackHandler(enabled = screen != "home", onBack = back)
    holder.SaveableStateProvider(screen) {
        when (screen) {
            "settings" -> SettingsScreen(back) { screen = "about" }
            "about" -> AboutScreen(back) { screen = "licenses" }
            "licenses" -> OssLicensesScreen(back)
            else -> HomeScreen(state, { screen = "settings" }, shutter::authorize, onRelease,
                { shutter.apply(ForceUse.deviceDefault()) }, shutter::refresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        content = content
    )
}

@Composable
private fun HomeScreen(state: ShutterUiState, onSettings: () -> Unit, onAuthorize: () -> Unit,
    onRelease: () -> Unit, onRestore: () -> Unit, onRefresh: () -> Unit) {
    ScreenScaffold(stringResource(R.string.app_name), actions = {
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            StatusRow(stringResource(when (state.status) {
                ShutterSound.Status.NOT_RUNNING -> R.string.shizuku_not_running
                ShutterSound.Status.NOT_AUTHORIZED -> R.string.shizuku_not_authorized
                ShutterSound.Status.READY -> R.string.shizuku_ready
            }), SettingsGroupPosition.Top)
            SettingsDividerGap()
            StatusRow(stringResource(if (ForceUse.deviceDefault() == ForceUse.FORCE_SYSTEM_ENFORCED)
                R.string.device_forced else R.string.device_not_forced), SettingsGroupPosition.Middle)
            SettingsDividerGap()
            val current = stringResource(when {
                state.busy -> R.string.current_working
                state.current == ForceUse.FORCE_SYSTEM_ENFORCED -> R.string.current_enforced
                state.current == ForceUse.FORCE_NONE -> R.string.current_none
                else -> R.string.current_unknown
            }) + if (!state.busy && state.current != null) " (FOR_SYSTEM=${state.current})" else ""
            StatusRow(current, SettingsGroupPosition.Bottom, true)
            if (state.error.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SettingsItemSurface(false, SettingsGroupPosition.Single, containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(state.error, Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.alreadyNone) {
                Spacer(Modifier.height(12.dp))
                StatusRow(stringResource(R.string.result_already_none), SettingsGroupPosition.Single)
            }
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAuthorize, enabled = !state.busy && state.status == ShutterSound.Status.NOT_AUTHORIZED,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_authorize))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRelease, enabled = state.canRelease, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_release))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRestore, enabled = state.canRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_restore))
            }
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_refresh))
            }
            Spacer(Modifier.height(24.dp))
            StatusRow(stringResource(R.string.caveats), SettingsGroupPosition.Single)
        }
    }
}

@Composable
private fun StatusRow(text: String, position: SettingsGroupPosition, emphasized: Boolean = false) {
    SettingsItemSurface(false, position) {
        Text(text, Modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = when {
                emphasized -> FontWeight.Bold
                position == SettingsGroupPosition.Single -> FontWeight.Normal
                else -> FontWeight.Medium
            })
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold(stringResource(R.string.settings_title), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutSectionTitle(stringResource(R.string.settings_general))
                SettingsEntry(stringResource(R.string.settings_language_title), stringResource(R.string.settings_language_description),
                    { openAppLanguageSettings(context) }, false, SettingsGroupPosition.Single)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutSectionTitle(stringResource(R.string.settings_information))
                SettingsEntry(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_description),
                    onAbout, false, SettingsGroupPosition.Single)
            }
        }
    }
}

private fun openAppLanguageSettings(context: Context) {
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

