package jp.linkserver.shutter

import jp.linkserver.shutter.maintain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CscEmulationTest {
    private class Values : CscEmulationValueStore { override var value = 1 }
    private class Journal : ShutterChangeStore {
        var saved = ShutterJournal()
        override fun load() = saved
        override fun save(journal: ShutterJournal) { saved = journal }
    }
    private class IntentStore(var saved: AutoMaintainState = AutoMaintainState()) : AutoMaintainStore {
        override fun load() = saved
        override fun save(state: AutoMaintainState) { saved = state }
    }
    private fun backend(emulation: CscEmulation, legacy: Boolean = false) =
        ShutterBackend(emulation.device, emulation, true, 11, legacy, bootCount = 1)

    @Test fun nonGalaxyDevicesCanSelectCscWithoutChangingTheirActualIdentity() {
        val actual = AppUserEnvironment("Google", 0)
        val settings = WarningDeveloperSettings(available = true, emulateCsc = true)
        assertTrue(settings.emulatesCsc)
        assertTrue(settings.simulated)
        assertTrue(settings.environment(actual).samsung)
        assertEquals(actual, settings.environment(actual).actual)
        assertEquals(0, settings.environment(actual).settingsUserId)
        assertFalse(settings.copy(available = false).emulatesCsc)
        assertEquals(actual, settings.copy(available = false).environment(actual).recognized)
    }

    @Test fun releaseAndRestoreUseTheDurableVirtualValueAndIgnoreLegacyAudioMigration() {
        val values = Values()
        val journal = Journal()
        val first = CscEmulation(values, journal)
        val initial = backend(first, legacy = true).inspect()
        assertEquals(ShutterMethod.GALAXY_CSC, initial.method)
        assertEquals(Enforcement.ENFORCED, initial.enforcement)
        assertEquals(1, initial.systemDefault)
        assertFalse(initial.cscAbsent)
        assertEquals(Enforcement.RELEASED, backend(first).release().enforcement)
        assertEquals(0, values.value)
        assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 1), journal.saved.change)
        val restarted = CscEmulation(values, journal)
        assertTrue(backend(restarted).inspect().restorable)
        backend(restarted).restore()
        assertEquals(1, values.value)
        assertNull(journal.saved.change)
    }

    @Test fun audioWritesAndCreatingOrDeletingVirtualKeysAreRejected() {
        val values = Values()
        val emulation = CscEmulation(values, Journal())
        assertThrows(IllegalStateException::class.java) { emulation.device.readAudio() }
        assertThrows(IllegalStateException::class.java) { emulation.device.writeAudio(0) }
        assertThrows(IllegalArgumentException::class.java) { emulation.device.writeCsc(null) }
        assertThrows(IllegalArgumentException::class.java) { emulation.device.writeCsc(11) }
        assertThrows(IllegalArgumentException::class.java) {
            emulation.save(ShutterJournal(true, ShutterChange(ShutterMethod.AUDIO_POLICY, 11)))
        }
        assertEquals(1, values.value)
    }

    @Test fun commonToggleAndBackgroundMaintenanceOperateOnTheSameVirtualKey() = runBlocking {
        val values = Values()
        val emulation = CscEmulation(values, Journal())
        val controller = AutoMaintainController(IntentStore(), AudioPolicyConnector {
            BackendPolicyConnection(backend(emulation), emulation) {}
        })
        controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11)
        assertEquals(0, values.value)
        controller.setEnabled(true)
        values.value = 1 // Simulate the system reasserting enforcement.
        assertEquals(MaintainResult.RESTORED, controller.maintain())
        assertEquals(0, values.value)
        controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11)
        assertEquals(1, values.value)
        assertNull(emulation.load().change)
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
    }

    @Test fun switchingStoresRequiresRestorationAndPausesBothAutomaticStates() = runBlocking {
        val values = Values()
        val emulation = CscEmulation(values, Journal())
        val simulatedIntent = IntentStore(AutoMaintainState(true, true))
        val realIntent = IntentStore()
        var simulated = true
        val intent = object : AutoMaintainStore {
            fun selected() = if (simulated) simulatedIntent else realIntent
            override fun load() = selected().load()
            override fun save(state: AutoMaintainState) = selected().save(state)
        }
        val controller = AutoMaintainController(intent, AudioPolicyConnector {
            check(simulated) // The test never performs a real device operation.
            BackendPolicyConnection(backend(emulation), emulation) {}
        })
        controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
        assertTrue(runCatching {
            controller.updateControlMethod(emulation, resetMaintenance = true) { simulated = false }
        }.exceptionOrNull() is ShutterMethodChangeBlocked)
        assertTrue(simulated)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        controller.updateControlMethod(emulation, resetMaintenance = true) { simulated = false }
        assertFalse(simulated)
        assertEquals(AutoMaintainState(), simulatedIntent.saved)
        assertEquals(AutoMaintainState(), realIntent.saved)
        assertEquals(AutoMaintainState(), controller.state.value)
        // Recreating the real controller cannot resume simulated automatic work.
        assertFalse(AutoMaintainController(realIntent, AudioPolicyConnector { null }).state.value.active)
    }

    @Test fun unconfirmedNonMainUserStillCannotReleaseButCanRestoreEmulatedChanges() = runBlocking {
        val values = Values()
        val emulation = CscEmulation(values, Journal())
        val consent = object : SamsungUserConsentStore {
            var value = SamsungUserConsent()
            override fun load() = value
            override fun save(consent: SamsungUserConsent) { value = consent }
        }
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 1), consent)
        val controller = AutoMaintainController(IntentStore(AutoMaintainState(true, true)), AudioPolicyConnector {
            BackendPolicyConnection(backend(emulation), emulation) {}
        }, releaseAuthorization = warning)
        assertThrows(SamsungUserConfirmationRequired::class.java) { controller.requestManual(ManualAction.RELEASE) }
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertEquals(1, values.value)
        val permit = warning.confirm(false)
        controller.performManual(controller.requestManual(ManualAction.RELEASE, permit), 11)
        assertEquals(0, values.value)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        assertEquals(1, values.value)
    }

    @Test fun automaticOfferReloadsTheSelectedEnvironmentWithoutConsumingTheOtherOffer() {
        var simulated = false
        var realStage = AutoMaintainOfferStage.READY
        var simulatedStage = AutoMaintainOfferStage.PENDING
        val offer = AutoMaintainOffer(object : AutoMaintainOfferStore {
            override fun load() = if (simulated) simulatedStage else realStage
            override fun save(stage: AutoMaintainOfferStage) {
                if (simulated) simulatedStage = stage else realStage = stage
            }
        })
        assertFalse(offer.pending.value)
        simulated = true
        offer.reload()
        assertTrue(offer.pending.value)
        offer.finish()
        simulated = false
        offer.reload()
        assertFalse(offer.pending.value)
        assertEquals(AutoMaintainOfferStage.READY, realStage)
        assertEquals(AutoMaintainOfferStage.HANDLED, simulatedStage)
    }
}
