package jp.linkserver.shutter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.linkserver.shutter.R
// Overview geometry and action cards ported from glyphvisualizer's AboutScreen.
@Composable
internal fun AboutSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun AboutSettingsActionCardContent(
    title: String,
    description: String,
    actionLabel: String,
    nothingStyleEnabled: Boolean,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 22.dp,
                vertical = 18.dp
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (nothingStyleEnabled) FontWeight.Normal else FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun AboutOverviewMosaic(
    channelName: String,
    channelLabel: String,
    versionName: String,
    simpleVersion: String,
    versionCode: Int,
    buildNumber: String,
    nothingStyleEnabled: Boolean,
    onVersionClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    val context = LocalContext.current
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBorder: BorderStroke? = null
    val titleFontFamily = if (nothingStyleEnabled) FontFamily.SansSerif else null
    val gap = 3.dp
    val cardRadius = 20.dp
    val heroHeight = 176.dp * maxOf(1f, LocalDensity.current.fontScale)
    val bridgeLength = 48.dp
    val bridgeOverlap = 1.dp
    val gutterColor = MaterialTheme.colorScheme.surface

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val squareSize = maxOf((maxWidth - gap) * 0.5f, 160.dp * LocalDensity.current.fontScale)
        val cardWidth = (maxWidth - gap) * 0.5f

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Surface(
                onClick = onVersionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroHeight),
                shape = RoundedCornerShape(cardRadius),
                color = cardColor,
                border = cardBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = heroHeight)
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(92.dp),
                        shape = CircleShape,
                        color = androidx.compose.ui.res.colorResource(R.color.ic_launcher_background),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = titleFontFamily,
                                fontWeight = if (nothingStyleEnabled) FontWeight.Normal else FontWeight.Bold,
                                fontSize = 27.sp,
                                lineHeight = 32.sp
                            )
                            val forkedFrom = stringResource(R.string.about_forked_from)
                            val linkColor = MaterialTheme.colorScheme.primary
                            Text(
                                text = buildAnnotatedString {
                                    append(forkedFrom)
                                    append(" ")
                                    val upstreamRepository = "soralis0912/ShutterSoundToggle"
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = "https://github.com/$upstreamRepository",
                                            styles = TextLinkStyles(SpanStyle(
                                                color = linkColor,
                                                textDecoration = TextDecoration.Underline
                                            )),
                                            linkInteractionListener = {
                                                openUrl(context, "https://github.com/$upstreamRepository")
                                            }
                                        )
                                    ) {
                                        append(upstreamRepository)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        val releaseLine = stringResource(
                            R.string.about_overview_release_line,
                            versionName,
                            versionCode
                        ).replace(' ', '\u00A0')
                        Text(
                            text = "$channelLabel $releaseLine",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(squareSize)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(squareSize),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Surface(
                        onClick = onChannelClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(squareSize),
                        shape = RoundedCornerShape(cardRadius),
                        color = cardColor,
                        border = cardBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.about_channel),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = channelName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = titleFontFamily,
                                fontWeight = if (nothingStyleEnabled) FontWeight.Normal else FontWeight.Bold,
                                fontSize = 25.sp,
                                lineHeight = 29.sp
                            )
                        }
                    }

                    Surface(
                        onClick = onVersionClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(squareSize),
                        shape = RoundedCornerShape(cardRadius),
                        color = cardColor,
                        border = cardBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.about_build),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = buildNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 16.sp
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.about_version),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.about_version_value,
                                        simpleVersion
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = if (nothingStyleEnabled) {
                                        FontFamily.SansSerif
                                    } else {
                                        null
                                    },
                                    fontWeight = if (nothingStyleEnabled) FontWeight.Normal else FontWeight.Bold,
                                    fontSize = 25.sp,
                                    lineHeight = 29.sp
                                )
                            }
                        }
                    }
                }

                val horizontalBridgeStart = (cardWidth - bridgeLength) * 0.5f
                val verticalBridgeStart = (squareSize - bridgeLength) * 0.5f

                Box(
                    modifier = Modifier
                        .offset(
                            x = horizontalBridgeStart,
                            y = -gap - bridgeOverlap
                        )
                        .width(bridgeLength)
                        .height(gap + bridgeOverlap * 2f)
                        .background(cardColor)
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = horizontalBridgeStart - gap * 0.5f,
                            y = -gap
                        )
                        .size(gap)
                        .background(gutterColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = horizontalBridgeStart + bridgeLength - gap * 0.5f,
                            y = -gap
                        )
                        .size(gap)
                        .background(gutterColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = cardWidth - bridgeOverlap,
                            y = verticalBridgeStart
                        )
                        .width(gap + bridgeOverlap * 2f)
                        .height(bridgeLength)
                        .background(cardColor)
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = cardWidth,
                            y = verticalBridgeStart - gap * 0.5f
                        )
                        .size(gap)
                        .background(gutterColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = cardWidth,
                            y = verticalBridgeStart + bridgeLength - gap * 0.5f
                        )
                        .size(gap)
                        .background(gutterColor, CircleShape)
                )
            }
        }
    }
}



