package jp.linkserver.shutter.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GoldyMigrationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val release = testUpdate()

    @Test fun legacyPreferenceKeysAndCacheSurviveFreshRepositoryWithoutRenotifying() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("goldy_legacy_migration_test", 0)
        val settings = context.getSharedPreferences("goldy_migration_target_test", 0)
        prefs.edit().clear().commit(); settings.edit().clear().commit()
        try {
            val saved = "{\"tag\":\"${release.release.tag}\",\"body\":\"legacy cache left unchanged\"}"
            val stamp = System.currentTimeMillis()
            prefs.edit().putString("cached_update", saved).putString("dismissed_tag", "V99.0.0-RELEASE")
                .putLong("last_check_ms", stamp).putString("last_check_version", "1.1.1-IntDev")
                .putBoolean("dev_ignore_version_comparison", true).putBoolean("dev_ignore_update_interval", false)
                .putString("hidden_banner_tag", release.release.tag).commit()
            val store = InMemoryUpdateStateStore(StoredUpdateState(ignoredTags = setOf("v88.0.0-release")))
            var calls = 0
            repeat(2) {
                val host = AppUpdatePreferences(context, prefs, settings, "1.1.1-IntDev")
                host.migrate(store)
                assertEquals(setOf("v99.0.0-release", "v88.0.0-release"), store.read().ignoredTags)
                val engine = Goldy.createWithSource(context, GoldyConfig(UPDATE_REPOSITORY, UPDATE_POLICY,
                    initialOptions = host.options), { calls++; ReleaseCatalog(listOf(release.release)) }, store)
                assertTrue(engine.check() is CheckOutcome.Available)
                assertEquals(1, calls) // only first start must refresh unauthenticated old metadata
                assertNull(engine.state.value.notificationUpdate)
                assertFalse(engine.state.value.showBanner)
                assertTrue(host.developerSettings.ignoreVersionComparison)
                assertFalse(host.developerSettings.ignoreInterval)
                assertEquals(saved, prefs.getString("cached_update", null))
                assertEquals(stamp, settings.getLong("legacy_last_success_ms", 0))
                assertEquals(release.release.tag, host.hiddenBannerTag)
            }
            val host = AppUpdatePreferences(context, prefs, settings, "1.1.1-IntDev")
            val engine = Goldy.createWithSource(context, GoldyConfig(UPDATE_REPOSITORY, UPDATE_POLICY,
                initialOptions = host.options), { calls++; ReleaseCatalog(listOf(release.release)) }, store)
            assertTrue(engine.check(CheckTrigger.Manual) is CheckOutcome.Available)
            assertEquals(2, calls)
            assertNotNull(engine.state.value.availableUpdate)
            assertNull(engine.state.value.notificationUpdate)
            engine.ignoreVersion("v77.0.0-Release")
            // Migration marker prevents reintroducing an ignored tag deliberately removed later.
            store.write(store.read().copy(ignoredTags = setOf("v77.0.0-release")))
            host.migrate(store)
            assertEquals(setOf("v77.0.0-release"), store.read().ignoredTags)
            assertFalse(AppUpdatePreferences(context, prefs, settings, "1.1.1-Release").developerSettings.available)
        } finally { prefs.edit().clear().commit(); settings.edit().clear().commit() }
    }

    @Test fun candidateMakesViewUpdatePrimaryAndRecheckDoesNotOpenDetails() {
        var state by mutableStateOf(GoldyState(release, release, true))
        var checks = 0
        var details = 0
        compose.setContent {
            ShutterTheme {
                AppUpdatePrompt(state, { checks++; CheckOutcome.Available(release) }, { details++ }, {}, {})
            }
        }
        compose.onNodeWithTag("goldy-notification-check").assertDoesNotExist()
        compose.onNodeWithTag("goldy-notification-details").assertIsEnabled()
        compose.onNodeWithTag("goldy-notification-recheck").performClick()
        compose.runOnIdle { assertEquals(1, checks); assertEquals(0, details) }
        compose.runOnIdle { state = state.copy(checking = true) }
        compose.onNodeWithTag("goldy-notification-recheck").assertIsNotEnabled()
        compose.onNodeWithTag("goldy-notification-details").performClick()
        compose.runOnIdle { assertEquals(1, details) }
    }

    @Test fun failedCheckKeepsCandidateAndRetryControl() {
        compose.setContent {
            ShutterTheme {
                AppUpdatePrompt(GoldyState(release, release, lastOutcome = CheckOutcome.Failure(GoldyError.Network)),
                    { CheckOutcome.Available(release) }, {}, {}, {})
            }
        }
        compose.onNodeWithTag("goldy-notification-details").assertIsEnabled()
        compose.onNodeWithTag("goldy-notification-recheck").assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.about_update_failed)).assertIsDisplayed()
    }
}
