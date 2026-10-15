package jp.linkserver.shutter

import jp.linkserver.shutter.maintain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeveloperEnvironmentBehaviorTest {
    private class Store : ShutterChangeStore {
        var journal = ShutterJournal(true)
        override fun load() = journal
        override fun save(journal: ShutterJournal) { this.journal = journal }
    }
    private class Device(var audio: Int = 11, var csc: String? = "1") : ShutterDevice {
        val writes = mutableListOf<String>()
        override fun readAudio() = audio
        override fun writeAudio(value: Int) { writes += "audio:$value"; audio = value }
        override fun readCsc() = csc
        override fun readCscInfo() = if (csc == null) GalaxyCscSetting(false, null) else null
        override fun writeCsc(value: Int?) { writes += "csc:$value"; csc = value?.toString() }
        override fun canWriteCsc() = true
    }
    private class IntentStore : AutoMaintainStore {
        var state = AutoMaintainState(true, true)
        override fun load() = state
        override fun save(state: AutoMaintainState) { this.state = state }
    }

    @Test fun simulatedSamsungSelectsCscInsteadOfAudioWhileTheActualManufacturerStaysUnchanged() {
        val actual = AppUserEnvironment("Google", 0)
        val device = Device(audio = 0)
        val ordinary = WarningDeveloperSettings(available = true).environment(actual)
        val simulated = WarningDeveloperSettings(true, true).environment(actual)
        assertEquals(ShutterMethod.AUDIO_POLICY, ShutterBackend(device, Store(), ordinary.samsung, 11).inspect().method)
        val csc = ShutterBackend(device, Store(), simulated.samsung, 11).inspect()
        assertEquals(ShutterMethod.GALAXY_CSC, csc.method)
        assertEquals(Enforcement.ENFORCED, csc.enforcement)
        assertEquals("Google", simulated.actual.manufacturer)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun simulatedSamsungUsesTheAbsentCscRuleAndExplicitAudioModeStillWorks() {
        val environment = WarningDeveloperSettings(true, true, true).environment(AppUserEnvironment("Google", 0))
        val device = Device(csc = null)
        val csc = ShutterBackend(device, Store(), environment.samsung, 11).release()
        assertTrue(csc.cscAbsent)
        assertTrue(device.writes.isEmpty())
        ShutterBackend(device, Store(), environment.samsung, 11, forceAudioPolicy = true).release()
        assertEquals(listOf("audio:0"), device.writes)
        assertEquals(0, environment.settingsUserId)
        assertEquals(1, environment.recognized.userId)
    }

    @Test fun simulatedNonMainUserGuardsTheRealCscRouteAndAutomaticMaintenance() = runBlocking {
        val environment = WarningDeveloperSettings(true, true, true).environment(AppUserEnvironment("Google", 0))
        val consentStore = object : SamsungUserConsentStore {
            var consent = SamsungUserConsent()
            override fun load() = consent
            override fun save(consent: SamsungUserConsent) { this.consent = consent }
        }
        val warning = SamsungUserWarning(environment.recognized, consentStore)
        val device = Device()
        val store = Store()
        val intent = IntentStore()
        val controller = AutoMaintainController(intent, AudioPolicyConnector {
            BackendPolicyConnection(ShutterBackend(device, store, environment.samsung, 11), store) {}
        }, releaseAuthorization = warning)
        assertFalse(controller.state.value.enabled)
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertThrows(SamsungUserConfirmationRequired::class.java) { controller.requestManual(ManualAction.RELEASE) }
        assertTrue(device.writes.isEmpty())
        val permit = warning.confirm(false)
        controller.performManual(controller.requestManual(ManualAction.RELEASE, permit), 11)
        assertEquals(listOf("csc:0"), device.writes)
        assertEquals(0, environment.settingsUserId)
        assertEquals(ShutterMethod.GALAXY_CSC, store.journal.change!!.method)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        assertEquals(listOf("csc:0", "csc:1"), device.writes)
    }

    @Test fun aManufacturerOverrideCannotSwitchAnUnrestoredTransaction() = runBlocking {
        val actual = AppUserEnvironment("Google", 0)
        var selected = WarningDeveloperSettings(available = true).environment(actual)
        val device = Device()
        val store = Store()
        val controller = AutoMaintainController(IntentStore(), AudioPolicyConnector {
            BackendPolicyConnection(ShutterBackend(device, store, selected.samsung, 11), store) {}
        })
        controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
        val simulated = WarningDeveloperSettings(true, true).environment(actual)
        assertTrue(runCatching {
            controller.updateControlMethod(store) { selected = simulated }
        }.exceptionOrNull() is ShutterMethodChangeBlocked)
        assertFalse(selected.samsung)
        assertEquals(ShutterMethod.AUDIO_POLICY, store.journal.change!!.method)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        controller.updateControlMethod(store) { selected = simulated }
        assertTrue(selected.samsung)
        assertFalse(controller.state.value.armed)
        assertEquals(ShutterMethod.GALAXY_CSC, ShutterBackend(device, store, selected.samsung, 11).inspect().method)
        assertEquals(listOf("audio:0", "audio:11"), device.writes)
    }
}
