package jp.linkserver.shutter.ui
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.linkserver.shutter.R
import jp.linkserver.goldy.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class GoldyAboutActionsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun oneCardActionCoversCheckCandidateAndSavedTransferWithoutAutoNavigation() {
        val candidate=UpdateCandidate(GitHubRelease(701,"v99.0.0-Release",url="https://github.com/example/app/releases/tag/v99.0.0-Release"))
        var available: UpdateCandidate? by mutableStateOf(null)
        var saved by mutableStateOf(false)
        var checks=0;var opens=0
        val check: suspend () -> CheckOutcome={checks++;available=candidate;CheckOutcome.Available(candidate)}
        compose.setContent { MaterialTheme { AboutScreen(onBack={}, onOssLicenses={}, availableUpdate=available, hasPendingInstallRequests=saved,
            onUpdateAvailable={opens++}, onCheckUpdates=check) } }
        fun label(id:Int)=compose.activity.getString(id)
        compose.onAllNodesWithText(label(R.string.about_check_updates)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.about_check_updates)).performScrollTo().performClick()
        compose.waitForIdle();assertEquals(1,checks);assertEquals(0,opens)
        compose.onAllNodesWithText(label(R.string.goldy_update_view)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.goldy_update_view)).performScrollTo().performClick()
        assertEquals(1,opens)
        // Pending APK / paused or restoring transfer remains accessible without a catalog candidate.
        compose.runOnIdle { available=null;saved=true }
        compose.onAllNodesWithText(label(R.string.goldy_update_view)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.goldy_update_view)).performScrollTo().performClick()
        assertEquals(2,opens);assertEquals(1,checks)
    }
}
