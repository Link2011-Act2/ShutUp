package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val release = GitHubRelease("v2.0.0-Beta2", "Camera settings and interface improvements.\n設定画面とシャッター音の操作を改善しました。",
        "${AppLinks.REPOSITORY}/releases/tag/v2.0.0-Beta2", true,
        intermediateNotes = listOf(ReleaseNote("v1.5.0", "Earlier improvements")))

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "update-design").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun bannerPromptIgnoreAndManualDetailsShareTheSameUpdate() {
        var updates by mutableStateOf(AppUpdateState(release, release, true))
        var blocked by mutableStateOf(false)
        compose.setContent {
            ShutterTheme {
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {}, updates,
                    { Result.success(release) }, { updates = updates.copy(showBanner = false) },
                    { updates = AppUpdateState(release) }, !blocked)
            }
        }
        compose.onNodeWithText(text(R.string.update_screen_title)).assertIsDisplayed()
        screenshot("banner")
        compose.runOnIdle { blocked = true }
        compose.onNodeWithText(text(R.string.update_screen_title)).assertDoesNotExist()
        compose.runOnIdle { blocked = false }
        compose.onNodeWithContentDescription(text(R.string.update_banner_dismiss)).performClick()
        compose.onNodeWithText(text(R.string.update_screen_title)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.update_action_description)).performClick()
        compose.onNodeWithText(text(R.string.update_confirm)).assertIsDisplayed()
        screenshot("prompt")
        compose.onNodeWithText(text(R.string.update_ignore)).performClick()
        compose.onNodeWithContentDescription(text(R.string.update_action_description)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        compose.onNodeWithText(text(R.string.about_check_updates)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("v2.0.0-Beta2").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.update_notes_title)).assertIsDisplayed()
        screenshot("details")
        compose.onNodeWithText(text(R.string.update_intermediate_notes_title)).performScrollTo().performClick()
        compose.onNodeWithTag("release-note-v1.5.0").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(noteViews().any { it.text.toString() == "Earlier improvements" }) }
        screenshot("intermediate")
        compose.onNodeWithText(text(R.string.update_intermediate_notes_title)).performScrollTo().performClick()
        compose.onNodeWithTag("release-note-v1.5.0").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
    }

    @Test fun downloadProgressKeepsTheCancelActionAvailable() {
        var cancellations = 0
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(release.copy(apkUrl = "${AppLinks.REPOSITORY}/releases/download/v2/ShutUp.apk"),
                    UpdateDownloadState(release.tag, true, 10_000_000, 30_000_000), {}, onAction = { cancellations++ })
            }
        }
        screenshot("downloading")
        compose.onNodeWithText(text(R.string.update_cancel_download)).assertIsEnabled().performClick()
        assertEquals(1, cancellations)
    }

    private fun noteViews(): List<TextView> {
        fun descendants(view: View): List<TextView> = when (view) {
            is TextView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else -> emptyList()
        }
        return descendants(compose.activity.window.decorView)
    }

    @Test fun markdownStylesAndIntermediateNotesRenderInsideTheReferenceLayout() {
        val markdown = """
            ## Improvements / 改善点
            **Camera controls** now work with the updated interface.

            - First improvement
            - Second improvement

            > Restart reminder

            `FOR_SYSTEM=0`

            [GitHub release](${release.url})

            | Feature | Status |
            | --- | --- |
            | Camera | Improved |
        """.trimIndent()
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(release.copy(notes = markdown, apkName = "ShutUp-v2.0.0-Beta2.apk",
                    apkUrl = "${AppLinks.REPOSITORY}/releases/download/v2/ShutUp.apk"),
                    UpdateDownloadState(), {}, onAction = {})
            }
        }
        compose.onNodeWithTag("latest-release-notes").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.update_asset_name, "ShutUp-v2.0.0-Beta2.apk"))
            .assertIsDisplayed()
        compose.runOnIdle {
            val view = noteViews().single { it.text.contains("Improvements / 改善点") }
            val spanned = view.text as Spanned
            assertFalse(spanned.toString().contains("##"))
            assertFalse(spanned.toString().contains("**"))
            val types = spanned.getSpans(0, spanned.length, Any::class.java).map { it.javaClass.simpleName }.toSet()
            for (type in listOf("HeadingSpan", "StrongEmphasisSpan", "BulletListItemSpan", "BlockQuoteSpan", "CodeSpan", "LinkSpan", "TableRowSpan")) {
                assertTrue("Missing rendered Markdown style: $type ($types)", type in types)
            }
            assertTrue(view.isTextSelectable)
            assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f,
                view.resources.displayMetrics), view.textSize, 0.1f)
        }
        screenshot("markdown")
        compose.onNodeWithText(text(R.string.update_intermediate_notes_title)).performScrollTo().performClick()
        compose.onNodeWithTag("release-note-v1.5.0").performScrollTo().assertIsDisplayed()
        screenshot("markdown-intermediate")
        compose.onNodeWithText(text(R.string.update_download_install)).assertIsDisplayed()
    }

    @Test fun emptyNotesAndInstallFeedbackKeepTheReleasePageActionAvailable() {
        var opens = 0
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(release.copy(notes = "", intermediateNotes = emptyList()),
                    UpdateDownloadState(), {}, text(R.string.update_permission_required), onAction = { opens++ })
            }
        }
        compose.runOnIdle {
            assertTrue(noteViews().any { it.text.toString() == text(R.string.about_update_no_notes) })
        }
        compose.onNodeWithText(text(R.string.update_intermediate_notes_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.update_permission_required)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about_open_release)).assertIsEnabled().performClick()
        assertEquals(1, opens)
        screenshot("empty-notes")
    }

    @Test fun releaseResponseAndSavedMetadataRoundTrip() {
        val json = """[
          {"tag_name":"v2.0.0","html_url":"${AppLinks.REPOSITORY}/releases/tag/v2.0.0","body":null,
           "assets":[{"name":"ShutUp.apk","browser_download_url":"${AppLinks.REPOSITORY}/releases/download/v2.0.0/ShutUp.apk"}]},
          {"tag_name":"v3.0.0","html_url":"${AppLinks.REPOSITORY}/releases/tag/v3.0.0","draft":true},
          {"tag_name":"nightly","html_url":"${AppLinks.REPOSITORY}/releases/tag/nightly"}
        ]"""
        val parsed = parseGitHubReleases(json).single()
        assertEquals("v2.0.0", parsed.tag)
        assertEquals("", parsed.notes)
        assertEquals("ShutUp.apk", parsed.apkName)
        val cached = parsed.copy(intermediateNotes = release.intermediateNotes)
        assertEquals(cached, decodeUpdate(encodeUpdate(cached)))
    }

    @Test fun persistedUpdatesExpireAfterInstallationAndInvalidApksAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("update_persistence_test", 0)
        prefs.edit().clear().commit()
        try {
            val store = AppUpdatePreferences(context, prefs, "1.0.0-IntDev")
            store.saveSuccessfulCheck(release)
            store.dismiss(release.tag)
            val restored = AppUpdatePreferences(context, prefs, "1.0.0-IntDev")
            assertEquals(release, restored.loadUpdate())
            assertFalse(restored.shouldCheck())
            assertTrue(restored.isDismissed(release.tag))
            val installed = AppUpdatePreferences(context, prefs, "2.0.0-Beta2")
            assertNull(installed.loadUpdate())
            assertTrue(installed.shouldCheck())
            verifyUpdateApk(context, File(context.applicationInfo.sourceDir))
            try {
                verifyUpdateApk(context, File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir))
                fail("An APK for another package must be rejected")
            } catch (_: IllegalStateException) { }
        } finally { prefs.edit().clear().commit() }
    }
}
