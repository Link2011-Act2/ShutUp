package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SettingsGroupPosition {
    Single,
    Top,
    Middle,
    Bottom
}

@Composable
fun SettingsDividerGap() {
    Box(modifier = Modifier.height(2.dp))
}

@Composable
fun SettingsItemSurface(
    nothingStyle: Boolean,
    position: SettingsGroupPosition,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val modifier = Modifier.fillMaxWidth()
    val shape = itemShape(position)
    val color = containerColor ?: settingsCardColor()
    val border = settingsCardBorder()

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content
        )
    }
}

@Composable
fun SettingsNothingGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = settingsCardColor(),
        border = settingsCardBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.outlineVariant
        ) {}
    }
}

@Composable
fun SettingsEntry(
    title: String,
    description: String,
    onClick: () -> Unit,
    nothingStyle: Boolean,
    position: SettingsGroupPosition,
    containerColor: Color? = null,
    enabled: Boolean = true
) {
    val chevronTint = if (nothingStyle) {
        if (isSystemInDarkTheme()) Color(0xFFEDEDED) else Color(0xFF1A1A1A)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    SettingsItemSurface(
        nothingStyle = nothingStyle,
        position = position,
        onClick = onClick,
        enabled = enabled,
        containerColor = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.38f)
                .padding(
                    horizontal = 22.dp,
                    vertical = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (nothingStyle) FontWeight.Normal else FontWeight.Bold
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                painter = androidx.compose.ui.res.painterResource(jp.linkserver.shutter.R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = chevronTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun itemShape(position: SettingsGroupPosition): RoundedCornerShape {
    val cornerRadius = 22.dp
    val innerRadius = 6.dp
    return when (position) {
        SettingsGroupPosition.Single -> RoundedCornerShape(cornerRadius)
        SettingsGroupPosition.Top -> RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        SettingsGroupPosition.Middle -> RoundedCornerShape(innerRadius)
        SettingsGroupPosition.Bottom -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
    }
}

@Composable
private fun settingsCardBorder(): BorderStroke? {
    return null
}

@Composable
private fun settingsCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

