package jp.linkserver.shutter.ui

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.AppLinks
import jp.linkserver.goldy.*
import jp.linkserver.shutter.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Run stages in separate instrumentation invocations with force-stop between them for process-death coverage. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GoldyProcessRestorationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("goldy_process_restore_test", 0)
    private val settings get() = context.getSharedPreferences("goldy_process_target_test", 0)
    private val repository = GitHubRepository("GoldyTests", "ProcessRestore")
    private val release get() = testUpdate().release.copy(url = "https://github.com/GoldyTests/ProcessRestore/releases/tag/v99.0.0-Release")
    private val store get() = UpdateStateStores.preferences(context, repository)

    @Test fun stage1SeedLegacyPreferences() = runBlocking<Unit> {
        store.write(StoredUpdateState())
        settings.edit().clear().commit()
        prefs.edit().clear().putString("cached_update", "legacy-original")
            .putString("dismissed_tag", release.tag).putString("last_check_version", "1.0.0-IntDev")
            .putLong("last_check_ms", System.currentTimeMillis())
            .putBoolean("dev_ignore_version_comparison", true).putBoolean("dev_ignore_update_interval", false)
            .putInt("seed_pid", Process.myPid()).commit().also { assertTrue(it) }
        println("Goldy restore seed pid=${Process.myPid()}")
    }

    @Test fun stage2RestoreLegacyPreferences() = runBlocking<Unit> {
            assertTrue(prefs.contains("seed_pid"))
            assertNotEquals(prefs.getInt("seed_pid", 0), Process.myPid())
            println("Goldy restore seed pid=${prefs.getInt("seed_pid", 0)} restored pid=${Process.myPid()}")
            val host = AppUpdatePreferences(context, prefs, settings, "1.1.1-IntDev")
            host.migrate(store)
            var calls = 0
            val engine = Goldy.createWithSource(context, GoldyConfig(repository, UPDATE_POLICY, initialOptions = host.options),
                { calls++; ReleaseCatalog(listOf(release)) }, store)
            assertTrue(engine.check() is CheckOutcome.Available)
            assertEquals(1, calls)
            assertNull(engine.state.value.notificationUpdate)
            assertTrue(host.developerSettings.ignoreVersionComparison)
            assertFalse(host.developerSettings.ignoreInterval)
            assertEquals("legacy-original", prefs.getString("cached_update", null))
            prefs.edit().putInt("migrated_pid", Process.myPid()).commit()
            Unit
    }

    @Test fun stage3RestoreGoldCacheWithoutNetwork() = runBlocking<Unit> {
        try {
            assertNotEquals(prefs.getInt("migrated_pid", 0), Process.myPid())
            val host = AppUpdatePreferences(context, prefs, settings, "1.1.1-IntDev")
            host.migrate(store)
            val engine = Goldy.createWithSource(context, GoldyConfig(repository, UPDATE_POLICY, initialOptions = host.options),
                { error("Valid persisted cache must avoid a network call") }, store)
            assertTrue(engine.check() is CheckOutcome.Available)
            assertNotNull(engine.state.value.availableUpdate)
            assertNull(engine.state.value.notificationUpdate)
            println("Goldy restored pid=${Process.myPid()}")
        } finally { prefs.edit().clear().commit(); settings.edit().clear().commit(); store.write(StoredUpdateState()) }
    }
}
