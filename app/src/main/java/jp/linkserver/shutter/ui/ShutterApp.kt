package jp.linkserver.shutter.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R

@Composable
fun ShutterApp(shutter: ShutterViewModel, onRelease: () -> Unit) {
    val state by shutter.state.collectAsStateWithLifecycle()
    ShutterNavigation(state, shutter::authorize, onRelease,
        { shutter.apply(ForceUse.deviceDefault()) }, shutter::refresh)
}

@Composable
internal fun ShutterNavigation(
    state: ShutterUiState, onAuthorize: () -> Unit, onRelease: () -> Unit,
    onRestore: () -> Unit, onRefresh: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    val holder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val back = {
        screen = when (screen) { "licenses" -> "about"; "about" -> "settings"; else -> "home" }
    }
    BackHandler(enabled = screen != "home", onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transitionSpec = {
                val direction = if (screenDepth(targetState) > screenDepth(initialState)) 1 else -1
                ((slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { direction * it / 5 } +
                    fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -direction * it / 5 } +
                        fadeOut(tween(140)))).using(null)
            },
            label = "screen_transition"
        ) { targetScreen ->
            holder.SaveableStateProvider(targetScreen) {
                when (targetScreen) {
                    "settings" -> SettingsScreen(back) { screen = "about" }
                    "about" -> AboutScreen(back) { screen = "licenses" }
                    "licenses" -> OssLicensesScreen(back)
                    else -> HomeScreen(state, { screen = "settings" }, { openShizuku(context) }, onAuthorize,
                        onRelease, onRestore, onRefresh)
                }
            }
        }
    }
}

private fun screenDepth(screen: String): Int = when (screen) {
    "settings" -> 1
    "about" -> 2
    "licenses" -> 3
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

