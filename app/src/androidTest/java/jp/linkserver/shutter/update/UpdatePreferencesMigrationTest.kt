package jp.linkserver.shutter.update

import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.shutter.ui.testUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdatePreferencesMigrationTest {
    @Test fun failedGoldWriteDoesNotCommitMigrationAndRetryPreservesAllFlags() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = context.getSharedPreferences("migration_failure_old", 0)
        val target = context.getSharedPreferences("migration_failure_new", 0)
        old.edit().clear().putString("dismissed_tag", "V2.0.0-BETA2").putString("cached_update", "original-json")
            .putBoolean("automatic_checks_enabled", false).putBoolean("notifications_enabled", false)
            .putBoolean("dev_ignore_version_comparison", true).putBoolean("dev_ignore_update_interval", true).commit()
        target.edit().clear().commit()
        try {
            var broken = true
            var saved = StoredUpdateState(ignoredTags = setOf("v1"))
            val store = object : UpdateStateStore {
                override suspend fun read() = saved
                override suspend fun write(state: StoredUpdateState) { if (broken) error("disk unavailable"); saved = state }
            }
            val host = AppUpdatePreferences(context, old, target, "1.1.1-IntDev")
            try { host.migrate(store); fail("Storage failure must propagate") } catch (_: IllegalStateException) { }
            assertFalse(target.getBoolean("migration_v1", false))
            broken = false
            host.migrate(store); host.migrate(store)
            assertEquals(setOf("v1", "v2.0.0-beta2"), saved.ignoredTags)
            assertFalse(host.options.automaticChecksEnabled)
            assertFalse(host.options.notificationsEnabled)
            assertTrue(host.developerSettings.ignoreVersionComparison)
            assertTrue(host.developerSettings.ignoreInterval)
            assertEquals("original-json", old.getString("cached_update", null))
            val restarted = AppUpdatePreferences(context, old, target, "1.1.1-IntDev")
            assertEquals(host.options, restarted.options)
            var calls = 0
            val engine = Goldy.createWithSource(context, GoldyConfig(UPDATE_REPOSITORY, UPDATE_POLICY,
                initialOptions = restarted.options), { calls++; ReleaseCatalog(listOf(testUpdate().release)) }, store)
            assertEquals(CheckOutcome.Disabled, engine.check())
            assertEquals(0, calls)
            assertTrue(engine.check(CheckTrigger.Manual) is CheckOutcome.Available)
            assertEquals(1, calls)
            assertNotNull(engine.state.value.availableUpdate)
            assertNull(engine.state.value.notificationUpdate)
        } finally { old.edit().clear().commit(); target.edit().clear().commit() }
    }
}
