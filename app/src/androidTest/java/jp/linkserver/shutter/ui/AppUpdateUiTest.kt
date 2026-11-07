package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.*
import jp.linkserver.goldy.GoldyState
import jp.linkserver.goldy.CheckOutcome
import jp.linkserver.goldy.ApkProgress
import jp.linkserver.goldy.ApkVerifier
import jp.linkserver.goldy.ApkVersionBindings
import jp.linkserver.goldy.AndroidApkInspector
import jp.linkserver.goldy.ApkException
import jp.linkserver.goldy.ApkFailure
import jp.linkserver.goldy.compose.UpdateTransferState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val release = testUpdate("v2.0.0-Beta2")

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Semantics can be idle before SurfaceFlinger presents the first Activity frame.
        Thread.sleep(1_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "update-design").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun bannerPromptIgnoreAndManualDetailsShareTheSameUpdate() {
        var updates by mutableStateOf(GoldyState(release, release, true))
        var blocked by mutableStateOf(false)
        compose.setContent {
            ShutterTheme {
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {}, updates,
                    { CheckOutcome.Available(release) }, { updates = updates.copy(showBanner = false) },
                    { updates = GoldyState(release) }, !blocked)
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
        compose.onNodeWithText(text(R.string.settings_about_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_check_updates)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("v2.0.0-Beta2").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.update_notes_title)).assertIsDisplayed()
        screenshot("details")
        compose.onNodeWithTag("history-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("history-2").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(noteViews().any { it.text.toString() == "Earlier improvements" }) }
        screenshot("intermediate")
        compose.onNodeWithTag("history-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("history-2").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
    }

    @Test fun downloadProgressKeepsTheCancelActionAvailable() {
        var cancellations = 0
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(testUpdate("v2.0.0-Beta2", true),
                    UpdateDownloadState(release.release.tag, UpdateTransferState.Downloading(ApkProgress(10_000_000, 30_000_000, 1)),
                        restoring = false, releaseIdentity = transferIdentity(testUpdate("v2.0.0-Beta2", true))), {}, onAction = { cancellations++ })
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
        var referenceTextPx = Float.NaN
        val markdown = """
            ## Improvements / 改善点
            **Camera controls** now work with the updated interface.

            - First improvement
            - Second improvement

            > Restart reminder

            `FOR_SYSTEM=0`

            [GitHub release](${release.release.url})

            | Feature | Status |
            | --- | --- |
            | Camera | Improved |
        """.trimIndent()
        compose.setContent {
            ShutterTheme {
                val expected = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.fontSize.toPx() }
                SideEffect { referenceTextPx = expected }
                UpdateOverviewContent(testUpdate("v2.0.0-Beta2", true).let { it.copy(release = it.release.copy(notes = markdown)) },
                    UpdateDownloadState(restoring = false), {}, onAction = {})
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
            assertTrue(referenceTextPx > 0f)
            assertEquals(referenceTextPx,view.textSize,0.1f)
        }
        screenshot("markdown")
        compose.onNodeWithTag("history-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("history-2").performScrollTo().assertIsDisplayed()
        screenshot("markdown-intermediate")
        compose.onNodeWithText(text(R.string.goldy_transfer_download)).performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyNotesAndInstallFeedbackKeepTheReleasePageActionAvailable() {
        var opens = 0
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(release.copy(release = release.release.copy(notes = ""), intermediateReleases = emptyList()),
                    UpdateDownloadState(restoring = false), {}, text(R.string.update_permission_required), onOpenUrl = { opens++ }, onAction = {})
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

    @Test fun pausedTransferShowsSavedProgressAndExplicitContinueOrCancel() {
        val actions = mutableListOf<jp.linkserver.goldy.compose.UpdateAction>()
        val progress = ApkProgress(15_000_000,30_000_000,1)
        val candidate = testUpdate("v2.0.0-Beta2",withApk=true)
        compose.setContent {
            ShutterTheme {
                UpdateOverviewContent(candidate,UpdateDownloadState(tag=candidate.release.tag,
                    releaseIdentity=transferIdentity(candidate),restoring=false,waiting=true,retainedProgress=progress,
                    transfer=UpdateTransferState.Failed(text(R.string.goldy_transfer_paused),jp.linkserver.goldy.compose.UpdateAction.Download)),
                    {},onAction=actions::add)
            }
        }
        val formatted = jp.linkserver.goldy.compose.DownloadProgressFormatter.format(progress,compose.activity.resources.configuration.locales[0])
        compose.onNodeWithText(formatted).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.update_cancel_download)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.goldy_transfer_restart)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf(jp.linkserver.goldy.compose.UpdateAction.CancelDownload,jp.linkserver.goldy.compose.UpdateAction.Download),actions)
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
        val cached = parsed.copy(intermediateNotes = listOf(ReleaseNote("v1.5.0", "Earlier improvements")))
        assertEquals(cached, decodeUpdate(encodeUpdate(cached)))
    }

    @Test fun persistedUpdatesExpireAfterInstallationAndInvalidApksAreRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inspector = AndroidApkInspector(context)
        val installed = inspector.installed()
        val own = inspector.inspect(File(context.applicationInfo.sourceDir))
        val ownRelease = testUpdate("v${installed.versionName}").release
        assertEquals(installed.packageName, ApkVerifier.verify(installed, own, ownRelease, ApkVersionBindings.Published).packageName)
        val foreignFile = File(context.cacheDir, "goldy-foreign-package.apk")
        InstrumentationRegistry.getInstrumentation().context.assets.open("goldy/foreign-package.apk").use { input ->
            foreignFile.outputStream().use { input.copyTo(it) }
        }
        val foreign = try { inspector.inspect(foreignFile) } finally { foreignFile.delete() }
        assertNotNull("Foreign fixture must have valid metadata and signatures", foreign)
        try { ApkVerifier.verify(installed, foreign, ownRelease, ApkVersionBindings.Published); fail("Foreign package") }
        catch (error: ApkException) { assertEquals(ApkFailure.PackageMismatch, error.reason) }
        val store = jp.linkserver.goldy.InMemoryUpdateStateStore(jp.linkserver.goldy.StoredUpdateState(
            cache = jp.linkserver.goldy.UpdateCache(jp.linkserver.shutter.update.UPDATE_REPOSITORY,
                jp.linkserver.shutter.update.UPDATE_POLICY.id, jp.linkserver.goldy.InstalledVersion("old", 0), "old", false,
                jp.linkserver.goldy.ReleaseCatalog(listOf(release.release)), System.currentTimeMillis())))
        var calls = 0
        val engine = jp.linkserver.goldy.Goldy.createWithSource(context, jp.linkserver.goldy.GoldyConfig(UPDATE_REPOSITORY, UPDATE_POLICY),
            { calls++; jp.linkserver.goldy.ReleaseCatalog(emptyList()) }, store)
        engine.restoreCachedState()
        assertNull(engine.state.value.availableUpdate)
        engine.check()
        assertEquals(1, calls)
    }
}
