package jp.linkserver.shutter

import android.provider.Settings
import jp.linkserver.shutter.maintain.AutoMaintainRuntime
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CscEmulationIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun virtualValueAndJournalSurviveRecreationWithoutTouchingTheRealKeyOrJournal() {
        val name = "csc_emulation_persistence_test"
        context.deleteSharedPreferences(name)
        val realValue = Settings.System.getString(context.contentResolver, GalaxyCscSetting.KEY)
        val realJournal = ShutterJournalFile(context).load()
        try {
            val emulation = CscEmulation.create(context, name)
            val first = ShutterBackend(emulation.device, emulation, true, 11)
            assertEquals(1, first.inspect().rawValue)
            first.release()
            val restarted = CscEmulation.create(context, name)
            val second = ShutterBackend(restarted.device, restarted, true, 11)
            assertEquals(0, second.inspect().rawValue)
            assertEquals(ShutterMethod.GALAXY_CSC, restarted.load().change!!.method)
            assertEquals(1, restarted.load().change!!.original)
            second.restore()
            assertEquals(1, CscEmulation.create(context, name).device.readCscInfo()!!.value)
            assertNull(restarted.load().change)
            assertEquals(realValue, Settings.System.getString(context.contentResolver, GalaxyCscSetting.KEY))
            assertEquals(realJournal, ShutterJournalFile(context).load())
        } finally {
            context.deleteSharedPreferences(name)
            listOf("", ".bak", ".new").forEach { File(context.noBackupFilesDir, "$name-journal$it").delete() }
        }
    }

    @Test fun runtimeKeepsVirtualHistoryPinnedAndAllowsAudioModeOnlyAfterRestoration() = runBlocking {
        val runtime = AutoMaintainRuntime.get(context)
        val original = runtime.warningDeveloperSettings.value
        val originalAudio = context.getSharedPreferences("galaxy_control", 0).getBoolean("forceAudioPolicy", false)
        val realJournal = ShutterJournalFile(context).load()
        val emulation = CscEmulation.create(context)
        val backend = ShutterBackend(emulation.device, emulation, true, 11)
        try {
            runtime.setWarningDeveloperSettings(WarningDeveloperSettings(available = true, emulateCsc = true))
            runtime.setGalaxyAudioPolicy(false)
            backend.release()
            assertEquals(0, emulation.device.readCscInfo()!!.value)
            assertTrue(runCatching {
                runtime.setWarningDeveloperSettings(runtime.warningDeveloperSettings.value.copy(emulateCsc = false))
            }.exceptionOrNull() is ShutterMethodChangeBlocked)
            assertTrue(runCatching { runtime.setGalaxyAudioPolicy(true) }.exceptionOrNull() is ShutterMethodChangeBlocked)
            assertTrue(runtime.warningDeveloperSettings.value.emulatesCsc)
            assertFalse(runtime.galaxySettings.value.forceAudioPolicy)
            assertEquals(realJournal, ShutterJournalFile(context).load())
            backend.restore()
            runtime.setGalaxyAudioPolicy(true)
            assertTrue(runtime.galaxySettings.value.forceAudioPolicy)
            assertNull(emulation.load().change)
            assertFalse(runtime.controller.state.value.enabled)
            runtime.setGalaxyAudioPolicy(false)
            assertEquals(1, emulation.device.readCscInfo()!!.value)
        } finally {
            if (emulation.load().change != null) backend.restore()
            runtime.setGalaxyAudioPolicy(originalAudio)
            runtime.setWarningDeveloperSettings(original)
        }
    }

    @Test fun emulationSettingPersistsAndIsIgnoredOutsideIntDev() {
        val name = "csc_emulation_channel_test"
        val prefs = context.getSharedPreferences(name, 0)
        prefs.edit().clear().commit()
        try {
            val dev = WarningDeveloperPreferences(context, prefs, "1.1.0-IntDev_rev0")
            dev.save(dev.load().copy(emulateCsc = true))
            val restarted = WarningDeveloperPreferences(context, prefs, "1.1.0-IntDev_rev0")
            assertTrue(restarted.load().emulatesCsc)
            assertTrue(restarted.load().environment(AppUserEnvironment("Google", 0)).samsung)
            for (version in listOf("1.1.0", "1.1.0-Beta", "1.1.0-Stable")) {
                val release = WarningDeveloperPreferences(context, prefs, version)
                assertFalse(release.load().emulatesCsc)
                release.save(WarningDeveloperSettings(available = true, emulateCsc = false))
                assertTrue(dev.load().emulatesCsc)
            }
        } finally { context.deleteSharedPreferences(name) }
    }
}
