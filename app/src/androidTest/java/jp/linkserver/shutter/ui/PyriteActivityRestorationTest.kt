package jp.linkserver.shutter.ui

import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.linkserver.shutter.MainActivity
import jp.linkserver.shutter.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercise the production Activity/Window, including initial insets after recreation. */
class PyriteActivityRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun recordGeometry(label: String) {
        val node = compose.onNode(hasScrollAction()).fetchSemanticsNode()
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        compose.runOnIdle {
            val content = compose.activity.findViewById<View>(android.R.id.content)
            val location = IntArray(2).also { content.getLocationOnScreen(it) }
            val bars = ViewCompat.getRootWindowInsets(content)?.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.i("PyriteMigrationGeometry", "$label value=${range.value()} max=${range.maxValue()} viewport=${node.boundsInRoot} content=${content.width}x${content.height}@${location.toList()} bars=$bars")
        }
    }

    @Test fun aboutRetainsBottomScrollAcrossActivityRecreation() {
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo()
        compose.waitForIdle()
        val scroll = compose.onNode(hasScrollAction())
        val before = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val viewport = scroll.fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText(text(R.string.about_oss_open)).fetchSemanticsNode().boundsInRoot
        recordGeometry("before")
        repeat(2) {
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
            val restored = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            recordGeometry("recreated-$it")
            assertEquals(before, restored.config[SemanticsProperties.VerticalScrollAxisRange].value(), 0f)
            assertEquals(viewport, restored.boundsInRoot)
            assertEquals(button, compose.onNodeWithText(text(R.string.about_oss_open)).fetchSemanticsNode().boundsInRoot)
        }
    }
}
