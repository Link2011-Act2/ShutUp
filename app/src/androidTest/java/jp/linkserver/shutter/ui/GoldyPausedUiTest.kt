package jp.linkserver.shutter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.*
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests host state-to-Elief wiring, not the already verified HTTP/crypto implementation. */
class GoldyPausedUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val candidate=UpdateCandidate(GitHubRelease(700,"v2.1.0-Release",notes="Release notes",
        url="https://github.com/example/app/releases/tag/v2.1.0-Release",
        assets=listOf(GitHubAsset(1,"shutup_update.apk","https://github.com/example/app/releases/download/v2.1.0-Release/shutup_update.apk",30_000_000,null))))
    @Test fun restoredPauseStaysInBottomActionsUntilExplicitResumeThenCancel() {
        var state by mutableStateOf(UpdateDownloadState(tag=candidate.release.tag,restoring=false,
            releaseIdentity=transferIdentity(candidate),waiting=true,retainedProgress=ApkProgress(15_000_000,30_000_000,1),
            transfer=UpdateTransferState.Failed("network")))
        val actions=mutableListOf<UpdateAction>();var error=0;var normal=0
        compose.setContent { MaterialTheme {
            error=MaterialTheme.colorScheme.error.toArgb();normal=MaterialTheme.colorScheme.primary.toArgb()
            UpdateOverviewContent(candidate,state,{},onAction={ action -> actions+=action
                if(action==UpdateAction.Download) state=state.copy(waiting=false,
                    transfer=UpdateTransferState.Downloading(checkNotNull(state.retainedProgress)))
            })
        } }
        fun text(id:Int)=compose.activity.getString(id)
        compose.onNodeWithTag("goldy-docked-actions").assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.goldy_transfer_restart)).assertCountEquals(1)
        compose.onAllNodesWithText(text(R.string.update_cancel_download)).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.goldy_transfer_paused)).assertIsDisplayed()
        compose.onNodeWithText("15.00 MB / 30.00 MB · 50.00%").assertIsDisplayed()
        fun barColor():Int {
            val pixels=compose.onNodeWithTag("goldy-download-progress").captureToImage().toPixelMap()
            return pixels[pixels.width/4,pixels.height/2].toArgb()
        }
        assertEquals(error,barColor());assertTrue(actions.isEmpty())
        compose.runOnIdle { state=state.copy() } // Re-observation never resumes.
        assertTrue(actions.isEmpty())
        compose.onNodeWithText(text(R.string.goldy_transfer_restart)).performClick()
        assertEquals(listOf(UpdateAction.Download),actions);assertEquals(normal,barColor())
        compose.onNodeWithText(text(R.string.update_cancel_download)).performClick()
        assertEquals(listOf(UpdateAction.Download,UpdateAction.CancelDownload),actions)
    }
}

