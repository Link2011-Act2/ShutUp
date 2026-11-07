package jp.linkserver.shutter.ui

import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.*
import jp.linkserver.goldy.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class GoldyActivityRestorationTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var original: Map<String, *>
    private lateinit var settings: SharedPreferences
    private lateinit var originalSettings: Map<String, *>
    private lateinit var store: UpdateStateStore
    private lateinit var originalGold: StoredUpdateState
    @get:Rule(order = 0) val preferences = object : ExternalResource() {
        override fun before() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            prefs = context.getSharedPreferences("github_release_updates", 0)
            original = prefs.all.toMap()
            settings = context.getSharedPreferences("goldy_host_updates", 0)
            originalSettings = settings.all.toMap()
            store = UpdateStateStores.preferences(context, UPDATE_REPOSITORY)
            originalGold = store.read()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val installed = InstalledVersion(requireNotNull(info.versionName), info.longVersionCode)
            val candidate = testUpdate()
            store.write(StoredUpdateState(UpdateCache(UPDATE_REPOSITORY, UPDATE_POLICY.id, installed,
                installed.name, false, ReleaseCatalog(listOf(candidate.release) + candidate.intermediateReleases), System.currentTimeMillis())))
            settings.edit().clear().commit()
            prefs.edit().putString("cached_update", "legacy-original")
                .putString("last_check_version", installedAppVersionName(context))
                .putLong("last_check_ms", System.currentTimeMillis()).remove("dismissed_tag")
                .putBoolean("dev_ignore_version_comparison", false).putBoolean("dev_ignore_update_interval", false).commit()
            Unit
        }
        override fun after() = runBlocking {
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> { @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>) }
            } }
            edit.commit()
            restorePreferences(settings, originalSettings)
            store.write(originalGold)
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualActivityRestoresPromptDetailsAndExpandedHistory() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("goldy-notification-icon").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.update_action_description)).performClick()
        compose.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
        compose.onNodeWithTag("goldy-notification-details").performClick()
        compose.onNodeWithTag("history-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("history-2").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("history-2").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        compose.onNodeWithTag("goldy-notification-icon").assertIsDisplayed()
    }
}
