package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

class DeveloperUpdateSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun optionsPersistIndependentlyAndCachedSameVersionFollowsTheToggle() {
        val prefs = context.getSharedPreferences("developer_updates_persistence_test", 0)
        prefs.edit().clear().commit()
        try {
            val release = GitHubRelease("v1.0.0-IntDev", "Notes",
                "${AppLinks.REPOSITORY}/releases/tag/v1.0.0-IntDev", true, publishedAt = 123)
            val store = AppUpdatePreferences(context, prefs, "1.0.0-IntDev")
            assertEquals(UpdateDeveloperSettings(available = true), store.developerSettings)
            store.saveSuccessfulCheck(release)
            assertNull(store.loadUpdate())
            assertFalse(store.shouldCheck())
            store.setIgnoreVersionComparison(true)
            val restarted = AppUpdatePreferences(context, prefs, "1.0.0-IntDev")
            assertEquals(UpdateDeveloperSettings(true, true, false), restarted.developerSettings)
            assertEquals(release, restarted.loadUpdate())
            assertFalse(restarted.shouldCheck())
            restarted.setIgnoreInterval(true)
            assertTrue(restarted.shouldCheck())
            restarted.setIgnoreVersionComparison(false)
            assertNull(restarted.loadUpdate())
            assertTrue(restarted.shouldCheck())
            restarted.setIgnoreInterval(false)
            assertFalse(restarted.shouldCheck())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun nonIntDevPreferencesIgnoreStoredOverridesAndRejectWrites() {
        val prefs = context.getSharedPreferences("developer_updates_channel_test", 0)
        prefs.edit().clear().commit()
        try {
            prefs.edit().putBoolean("dev_ignore_version_comparison", true)
                .putBoolean("dev_ignore_update_interval", true).commit()
            for (version in listOf("1.0.0", "1.0.0-Release", "1.0.0-Stable", "1.0.0-Beta")) {
                val store = AppUpdatePreferences(context, prefs, version)
                store.saveSuccessfulCheck(GitHubRelease("v$version", "Notes", "${AppLinks.REPOSITORY}/releases/tag/v$version"))
                assertEquals(UpdateDeveloperSettings(), store.developerSettings)
                assertNull(store.loadUpdate())
                assertFalse(store.shouldCheck())
                store.setIgnoreInterval(false)
                store.setIgnoreVersionComparison(false)
                assertTrue(prefs.getBoolean("dev_ignore_update_interval", false))
                assertTrue(prefs.getBoolean("dev_ignore_version_comparison", false))
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun developerSelectionUsesPublicationTimeAndAcceptsUnversionedReleaseTags() {
        val json = """[
          {"tag_name":"v9.0.0","html_url":"${AppLinks.REPOSITORY}/releases/tag/v9.0.0","published_at":"2026-01-01T00:00:00Z"},
          {"tag_name":"test-update","html_url":"${AppLinks.REPOSITORY}/releases/tag/test-update","published_at":"2026-02-01T00:00:00Z"},
          {"tag_name":"draft","html_url":"${AppLinks.REPOSITORY}/releases/tag/draft","draft":true,"published_at":"2026-03-01T00:00:00Z"},
          {"tag_name":"external","html_url":"https://example.com/releases/tag/external","published_at":"2026-04-01T00:00:00Z"}
        ]"""
        assertEquals(listOf("v9.0.0"), parseGitHubReleases(json).map { it.tag })
        val releases = parseGitHubReleases(json, allowUnversionedTags = true)
        assertEquals(2, releases.size)
        val update = selectReleaseUpdate(releases, "10.0.0-IntDev", true)!!
        assertEquals("test-update", update.tag)
        assertTrue(update.publishedAt > releases.first().publishedAt)
        assertEquals(update, decodeUpdate(encodeUpdate(update)))
        assertNull(selectReleaseUpdate(releases, "10.0.0-Beta", true))
    }

    @Test fun developerTogglesWorkThroughSettingsAndDisappearInOtherChannels() {
        val prefs = context.getSharedPreferences("developer_updates_ui_test", 0)
        prefs.edit().clear().commit()
        try {
            val store = AppUpdatePreferences(context, prefs, "1.0.0-IntDev")
            var settings by mutableStateOf(store.developerSettings)
            compose.setContent {
                ShutterTheme {
                    ShutterNavigation(ShutterUiState(), {}, {}, {}, {}, developerSettings = settings,
                        onIgnoreVersionComparisonChange = { store.setIgnoreVersionComparison(it); settings = store.developerSettings },
                        onIgnoreIntervalChange = { store.setIgnoreInterval(it); settings = store.developerSettings })
                }
            }
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.developer_ignore_version_title)).performScrollTo()
                .assertIsOff().performClick().assertIsOn()
            compose.onNodeWithText(text(R.string.developer_ignore_interval_title)).performScrollTo()
                .assertIsOff().performClick().assertIsOn()
            assertEquals(UpdateDeveloperSettings(true, true, true),
                AppUpdatePreferences(context, prefs, "1.0.0-IntDev").developerSettings)
            compose.waitForIdle()
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val directory = File(context.getExternalFilesDir(null), "developer-updates").apply { mkdirs() }
            File(directory, "settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.developer_ignore_version_title)).performScrollTo().assertIsOn()
            compose.runOnIdle { settings = AppUpdatePreferences(context, prefs, "1.0.0-Beta").developerSettings }
            compose.onNodeWithText(text(R.string.settings_developer)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.developer_ignore_version_title)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.developer_ignore_interval_title)).assertDoesNotExist()
        } finally { prefs.edit().clear().commit() }
    }
}
