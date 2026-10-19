package jp.linkserver.shutter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import jp.linkserver.shutter.GitHubRelease
import jp.linkserver.shutter.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PyriteAboutBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun updateFailureRetryAndCancellationKeepTheHostContract() {
        var pending = CompletableDeferred<Result<GitHubRelease?>>()
        var requests = 0
        var navigations = 0
        compose.setContent { ShutterTheme {
            AboutScreen({}, {}, { requests++; pending.await() }, { navigations++ })
        } }
        val check = compose.onNodeWithText(text(R.string.about_check_updates))
        check.performScrollTo().performClick().assertIsNotEnabled()
        check.performClick()
        compose.runOnIdle { assertEquals(1, requests); pending.complete(Result.failure(IllegalStateException("fixture"))) }
        compose.onNodeWithText(text(R.string.about_update_failed)).assertExists()
        check.assertIsEnabled()
        compose.runOnIdle { pending = CompletableDeferred() }
        check.performClick()
        compose.runOnIdle { pending.cancel() }
        check.assertIsEnabled()
        compose.onNodeWithText(text(R.string.about_update_failed)).assertExists()
        compose.runOnIdle { pending = CompletableDeferred() }
        check.performClick()
        compose.runOnIdle { pending.complete(Result.success(GitHubRelease("v9.0.0", "fixture",
            "https://github.com/Link2011-Act2/ShutUp/releases/tag/v9.0.0"))) }
        check.assertIsEnabled()
        compose.runOnIdle { assertEquals(3, requests); assertEquals(1, navigations) }
        compose.onNodeWithText(compose.activity.getString(R.string.about_update_available, "v9.0.0")).assertExists()
    }

    @Test fun versionDialogAndScrollRestoreWithTheNavigationSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ShutterTheme { ShutterNavigation(jp.linkserver.shutter.ShutterUiState(), {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        compose.onNodeWithText(text(R.string.about_version)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.about_version_details_title)).assertIsDisplayed()
        compose.onNodeWithText(text(android.R.string.ok)).performClick()
        compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo()
        val before = compose.onNodeWithText(text(R.string.about_oss_open)).fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.about_oss_open)).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText(text(R.string.about_oss_open)).fetchSemanticsNode().boundsInRoot)
    }
}
