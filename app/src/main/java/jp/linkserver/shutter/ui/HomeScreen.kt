package jp.linkserver.shutter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterSound
import jp.linkserver.shutter.ShutterUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: ShutterUiState,
    onSettings: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAuthorize: () -> Unit,
    onRelease: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
    deviceDefault: Int? = ForceUse.deviceDefault(),
    updateAction: @Composable RowScope.() -> Unit = {},
    updateBanner: @Composable () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                actions = {
                    updateAction()
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            updateBanner()
            HomeStateHero(state, deviceDefault, onOpenShizuku, onAuthorize, onRelease, onRestore, onRefresh)
            if (state.error.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(24.dp)) {
                    Text(state.error, Modifier.fillMaxWidth().padding(20.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!state.busy && state.current == ForceUse.FORCE_NONE &&
                deviceDefault == ForceUse.FORCE_NONE) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.result_already_none), Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            HomeSystemStatus(state, deviceDefault, onRefresh)
            Spacer(Modifier.height(16.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeIconBubble(rememberVectorPainter(Icons.Default.Info), 36)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.home_restart_note),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.home_uninstall_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HomeUsageNotes()
        }
    }
}

@Composable
private fun HomeStateHero(
    state: ShutterUiState, deviceDefault: Int?,
    onOpenShizuku: () -> Unit, onAuthorize: () -> Unit,
    onRelease: () -> Unit, onRestore: () -> Unit, onRefresh: () -> Unit
) {
    val isEnforced = state.status == ShutterSound.Status.READY &&
        state.current == ForceUse.FORCE_SYSTEM_ENFORCED
    val isReleased = state.status == ShutterSound.Status.READY &&
        state.current == ForceUse.FORCE_NONE
    val isUnenforcedByDefault = isReleased && deviceDefault == ForceUse.FORCE_NONE
    val isUnknown = state.status != ShutterSound.Status.READY || state.current == null
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        if (isReleased) colors.primaryContainer else colors.surfaceContainerHigh,
        tween(700, easing = FastOutSlowInEasing), label = "shutter card background")
    val contentColor by animateColorAsState(
        if (isReleased) colors.onPrimaryContainer else colors.onSurface,
        tween(700, easing = FastOutSlowInEasing), label = "shutter card content")
    val iconColor by animateColorAsState(
        if (isReleased) colors.primary else colors.onSurfaceVariant,
        tween(700, easing = FastOutSlowInEasing), label = "shutter status icon")
    val emblemColor by animateColorAsState(
        when {
            isReleased -> colors.primary.copy(alpha = 0.10f)
            isEnforced && colors.surface.luminance() > 0.5f ->
                lerp(colors.surfaceContainerHighest, colors.onSurfaceVariant, 0.18f)
            else -> colors.surfaceContainerHighest
        },
        tween(700, easing = FastOutSlowInEasing), label = "shutter status shape color")
    val icon = when {
        isUnknown -> R.drawable.ic_help
        isReleased -> R.drawable.ic_check_circle
        else -> R.drawable.ic_error
    }
    val label = when {
        state.busy -> R.string.home_working
        isUnknown -> R.string.home_unknown
        state.current == ForceUse.FORCE_SYSTEM_ENFORCED -> R.string.home_enforced
        isUnenforcedByDefault -> R.string.home_default_not_enforced
        state.current == ForceUse.FORCE_NONE -> R.string.home_not_enforced
        else -> R.string.home_other_policy
    }
    val subtitle = when {
        state.busy -> R.string.home_applying
        state.status == ShutterSound.Status.NOT_RUNNING -> R.string.home_start_shizuku_hint
        state.status == ShutterSound.Status.NOT_AUTHORIZED -> R.string.home_authorize_hint
        state.current == null -> R.string.home_refresh_hint
        isEnforced -> R.string.home_enforced_hint
        isUnenforcedByDefault -> R.string.home_default_not_enforced_hint
        isReleased -> R.string.home_released_hint
        else -> R.string.home_other_policy_hint
    }
    val subtitleText = stringResource(subtitle)
    val description = if (deviceDefault != null && !state.busy && state.status == ShutterSound.Status.READY &&
        state.current == deviceDefault) {
        stringResource(R.string.home_with_default, subtitleText)
    } else subtitleText
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        color = containerColor, contentColor = contentColor) {
        Column(Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_shutter_sound),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.displayMedium,
                        fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_force_system,
                        state.current?.toString() ?: "?"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = contentColor.copy(alpha = 0.72f))
                }
                HomeStatusEmblem(isReleased, icon, emblemColor, iconColor)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f))
            if (deviceDefault == null) {
                Text(stringResource(R.string.device_default_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(8.dp))
            HomeHeroAction(state, deviceDefault, onOpenShizuku, onAuthorize, onRelease, onRestore, onRefresh)
        }
    }
}

@Composable
private fun HomeHeroAction(
    state: ShutterUiState, deviceDefault: Int?,
    onOpenShizuku: () -> Unit, onAuthorize: () -> Unit,
    onRelease: () -> Unit, onRestore: () -> Unit, onRefresh: () -> Unit
) {
    val restore = state.current == ForceUse.FORCE_NONE
    val label = when {
        state.busy -> R.string.home_working
        state.status == ShutterSound.Status.NOT_RUNNING -> R.string.home_open_shizuku
        state.status == ShutterSound.Status.NOT_AUTHORIZED -> R.string.action_authorize
        state.current == null -> R.string.action_refresh
        restore -> R.string.home_restore
        else -> R.string.action_release
    }
    val action = when {
        state.status == ShutterSound.Status.NOT_RUNNING -> onOpenShizuku
        state.status == ShutterSound.Status.NOT_AUTHORIZED -> onAuthorize
        state.current == null -> onRefresh
        restore -> onRestore
        else -> onRelease
    }
    val enabled = !state.busy && !(state.status == ShutterSound.Status.READY &&
        restore && (deviceDefault == null || state.current == deviceDefault))
    Button(
        onClick = action, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                color = LocalContentColor.current)
        } else {
            val icon = when {
                state.status == ShutterSound.Status.NOT_RUNNING -> painterResource(R.drawable.ic_open_in_new)
                state.status == ShutterSound.Status.NOT_AUTHORIZED -> painterResource(R.drawable.ic_security)
                state.status == ShutterSound.Status.READY && state.current == null ->
                    rememberVectorPainter(Icons.Default.Refresh)
                state.status == ShutterSound.Status.READY && restore -> painterResource(R.drawable.ic_restore)
                else -> painterResource(R.drawable.ic_shutter_off)
            }
            Icon(icon, null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeSystemStatus(state: ShutterUiState, deviceDefault: Int?, onRefresh: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_system_status), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                }
            }
            HomeStatusItem(
                if (state.status == ShutterSound.Status.READY) rememberVectorPainter(Icons.Default.Check)
                    else painterResource(R.drawable.ic_warning_amber),
                stringResource(R.string.home_shizuku),
                stringResource(when (state.status) {
                    ShutterSound.Status.READY -> R.string.home_connected
                    ShutterSound.Status.NOT_AUTHORIZED -> R.string.home_permission_needed
                    ShutterSound.Status.NOT_RUNNING -> R.string.home_not_running
                }),
                connected = state.status == ShutterSound.Status.READY,
                error = state.status == ShutterSound.Status.NOT_RUNNING
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            HomeStatusItem(rememberVectorPainter(Icons.Default.Settings), stringResource(R.string.home_device_default),
                stringResource(when (deviceDefault) {
                    ForceUse.FORCE_SYSTEM_ENFORCED -> R.string.home_default_enforced
                    ForceUse.FORCE_NONE -> R.string.home_default_not_enforced
                    else -> R.string.home_unknown
                }))
        }
    }
}

@Composable
private fun HomeStatusItem(
    icon: Painter, title: String, detail: String, connected: Boolean = false, error: Boolean = false
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeIconBubble(icon, 40, connected, error)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeIconBubble(icon: Painter, size: Int, connected: Boolean = false, error: Boolean = false) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(Modifier.size(size.dp), shape = CircleShape,
        color = if (error) MaterialTheme.colorScheme.errorContainer else if (connected) {
            if (dark) Color(0xFF284D35) else Color(0xFFD3E9D5)
        } else MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (error) MaterialTheme.colorScheme.onErrorContainer else if (connected) {
                if (dark) Color(0xFFB7E0C0) else Color(0xFF193C2B)
            } else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun HomeUsageNotes() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Surface(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeIconBubble(painterResource(R.drawable.ic_menu_book), 36)
                Text(stringResource(R.string.home_before_using), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    stringResource(if (expanded) R.string.home_collapse_notes else R.string.home_expand_notes))
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.caveats), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
