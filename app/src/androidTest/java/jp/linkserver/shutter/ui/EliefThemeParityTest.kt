package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.pyrite.settings.SettingsGroup
import jp.linkserver.pyrite.settings.SettingsNavigationItem
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.UpdateDownloadState
import jp.linkserver.shutter.update.transferIdentity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Render the published Pyrite row and actual Elief/host cards in the same theme and window. */
class EliefThemeParityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val update = testUpdate("v2.0.0-Beta2", withApk = true)
    private fun text(id: Int) = compose.activity.getString(id)
    private val directory get() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "theme-parity/${InstrumentationRegistry.getArguments().getString("palettePhase", "normal")}").apply { mkdirs() }

    /** Presentation-only record: no file, SDK authority, verification bypass or installer invocation. */
    private fun presentationRequest() = InstallRequest::class.java.getConstructor(String::class.java, Uri::class.java, ApkMetadata::class.java)
        .newInstance("theme-fixture", Uri.parse("content://theme-fixture/unavailable"),
            ApkMetadata("ui.fixture", "2.0.0-Beta2", 0, ApkSigners(setOf("a".repeat(64)), false)))

    private fun assertFill(node: SemanticsNodeInteraction, color: Int, name: String, save: Boolean = true) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("$name expected fill ${color.toUInt().toString(16)}", pixels.count { it == color } > 100)
        if (save) File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun verify(dark: Boolean, dynamic: Boolean) {
        val prefix = "${if (dark) "dark" else "light"}-${if (dynamic) "dynamic" else "static"}"
        var pane by mutableIntStateOf(0)
        lateinit var colors: ColorScheme
        var card = 0
        var cancellations = 0
        val request = presentationRequest()
        compose.setContent {
            ShutterTheme(darkTheme = dark, dynamicColor = dynamic) {
                val scheme = MaterialTheme.colorScheme
                val style = LocalUpdateCardStyle.current
                val shape = MaterialTheme.shapes.large
                SideEffect {
                    colors = scheme
                    card = (if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest).toArgb()
                    assertEquals(card, style.containerColor.toArgb())
                    assertSame(shape, style.shape)
                }
                if (pane == 0) Surface(color = scheme.surface) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsGroup(Modifier.testTag("pyrite-reference")) {
                            item("reference") { SettingsNavigationItem("Pyrite reference", {}) }
                        }
                        AppUpdateBanner(update, {}, {})
                        RetainedInstallRequests(UpdateDownloadState(requests = listOf(request), restoring = false), {}, {})
                    }
                } else UpdateOverviewContent(update,
                    UpdateDownloadState(update.release.tag, UpdateTransferState.Downloading(ApkProgress(10, 30, 1)),
                        restoring = false, releaseIdentity = transferIdentity(update)), {}, onAction = {
                        assertEquals(UpdateAction.CancelDownload, it); cancellations++
                    })
            }
        }
        compose.waitForIdle()
        for ((tag, name) in listOf("pyrite-reference" to "pyrite", "goldy-notification-banner" to "banner", "retained-install-requests" to "retained")) {
            val node = compose.onNodeWithTag(tag).performScrollTo()
            assertFill(node, card, "$prefix-$name")
        }
        compose.runOnIdle { pane = 1 }
        compose.onNodeWithTag("latest-release-notes").performScrollTo()
        assertFill(compose.onNodeWithTag("latest-release-notes"), card, "$prefix-notes")
        compose.onNodeWithTag("history-toggle").performScrollTo()
        assertFill(compose.onNodeWithTag("history-toggle"), card, "$prefix-history")
        compose.onNodeWithTag("history-toggle").performClick()
        compose.onNodeWithTag("history-2").performScrollTo().assertIsDisplayed()
        // Published Elief uses the canvas role for inner history rows, like nested Pyrite surfaces.
        assertFill(compose.onNodeWithTag("history-2"), colors.surface.toArgb(), "$prefix-history-inner")
        val inline = compose.onAllNodesWithTag("goldy-inline-actions").fetchSemanticsNodes().isNotEmpty()
        if (inline) {
            compose.onNodeWithTag("goldy-inline-actions").performScrollTo()
            assertFill(compose.onNodeWithTag("goldy-inline-actions"), card, "$prefix-inline-actions")
        } else {
            // Docked actions form the screen chrome, not a card. Keep Pyrite's canvas role.
            assertFill(compose.onNodeWithTag("goldy-docked-actions"), colors.surface.toArgb(), "$prefix-docked-actions")
        }
        compose.onNodeWithText(text(R.string.update_cancel_download)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, cancellations)
    }

    @Test fun lightDynamic() = verify(false, true)
    @Test fun lightStatic() = verify(false, false)
    @Test fun darkDynamic() = verify(true, true)
    @Test fun darkStatic() = verify(true, false)

    @Test fun openDialogTracksThemeChangesAndKeepsEveryActionReachable() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var state by mutableStateOf(GoldyState(update, update))
        lateinit var colors: ColorScheme
        var checks = 0; var details = 0; var ignored = 0
        compose.setContent {
            ShutterTheme(darkTheme = dark, dynamicColor = dynamic) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { colors = scheme }
                Surface(Modifier.fillMaxSize(), color = scheme.surface) { Text("Parent surface") }
                if (visible) AppUpdatePrompt(state, { checks++; CheckOutcome.Available(update) },
                    { details++ }, { ignored++ }, { visible = false })
            }
        }
        for ((newDark, newDynamic) in listOf(false to false, true to false, true to true, false to true)) {
            compose.runOnIdle { dark = newDark; dynamic = newDynamic }
            compose.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
            compose.onNodeWithTag("goldy-notification-check").assertDoesNotExist()
            compose.onNodeWithTag("goldy-notification-details").performScrollTo().assertIsEnabled()
            val name = "dialog-${if (newDark) "dark" else "light"}-${if (newDynamic) "dynamic" else "static"}"
            assertFill(compose.onNodeWithTag("goldy-update-check-dialog"), colors.surfaceContainerHigh.toArgb(), name)
            assertFill(compose.onNodeWithTag("goldy-notification-details"), colors.primary.toArgb(), "$name-primary")
            assertFill(compose.onNodeWithTag("goldy-notification-details"), colors.onPrimary.toArgb(), "$name-on-primary", false)
            assertFill(compose.onNodeWithTag("goldy-update-check-dialog"), colors.onSurface.toArgb(), "$name-on-surface", false)
        }
        compose.onNodeWithTag("goldy-notification-recheck").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks); assertEquals(0, details); state = state.copy(checking = true) }
        compose.onNodeWithTag("goldy-notification-recheck").assertIsNotEnabled()
        compose.onNodeWithTag("goldy-notification-details").performScrollTo().performClick()
        compose.onNodeWithTag("goldy-notification-ignore").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, details); assertEquals(1, ignored) }
        compose.onNodeWithText(text(android.R.string.cancel)).performClick()
        compose.onNodeWithTag("goldy-update-check-dialog").assertDoesNotExist()
    }
}
