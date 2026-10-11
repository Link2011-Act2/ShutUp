package jp.linkserver.shutter.maintain

import org.junit.Assert.*
import org.junit.Test

class AutoMaintainOfferTest {
    private class Store(var saved: AutoMaintainOfferStage = AutoMaintainOfferStage.READY) : AutoMaintainOfferStore {
        var failSave = false
        override fun load() = saved
        override fun save(stage: AutoMaintainOfferStage) { check(!failSave); saved = stage }
    }
    private fun AutoMaintainOffer.release(enabled: Boolean = false) =
        manualFinished(ManualAction.RELEASE, Result.success(0), 11, AutoMaintainState(enabled, true))

    @Test fun firstSuccessfulReleasePersistsPendingAcrossRecreation() {
        val store = Store()
        val offer = AutoMaintainOffer(store)
        assertFalse(offer.pending.value)
        offer.release()
        assertTrue(offer.pending.value)
        assertTrue(AutoMaintainOffer(store).pending.value)
        offer.release()
        assertEquals(AutoMaintainOfferStage.PENDING, store.saved)
    }

    @Test fun failuresRestorationDefaultZeroAndStaleManualIntentDoNotTrigger() {
        val offer = AutoMaintainOffer(Store())
        offer.manualFinished(ManualAction.RELEASE, Result.failure(IllegalStateException()), 11, AutoMaintainState(false, true))
        offer.manualFinished(ManualAction.RESTORE, Result.success(0), 0, AutoMaintainState())
        offer.manualFinished(ManualAction.RELEASE, Result.success(0), 0, AutoMaintainState(false, true))
        offer.manualFinished(ManualAction.RELEASE, Result.success(0), null, AutoMaintainState(false, true))
        offer.manualFinished(ManualAction.RELEASE, Result.success(0), 11, AutoMaintainState(false, false))
        assertFalse(offer.pending.value)
        offer.release()
        assertTrue(offer.pending.value)
    }

    @Test fun notNowNeverEnablesAndDoesNotAskAfterAnotherReleaseOrRestart() {
        val store = Store()
        val offer = AutoMaintainOffer(store)
        offer.release()
        offer.respond(false) { fail("Not now must not enable maintenance") }
        assertFalse(offer.pending.value)
        val restored = AutoMaintainOffer(store)
        restored.release()
        assertFalse(restored.pending.value)
        assertEquals(AutoMaintainOfferStage.HANDLED, store.saved)
    }

    @Test fun maintainTurnsTheExistingSettingOnAndArmsIt() {
        var settings = AutoMaintainState(false, true)
        val controller = AutoMaintainController(object : AutoMaintainStore {
            override fun load() = settings
            override fun save(state: AutoMaintainState) { settings = state }
        }, AudioPolicyConnector { error("Setting the toggle must not require a Shizuku connection") })
        val offer = AutoMaintainOffer(Store())
        offer.release()
        offer.respond(true) { controller.setEnabled(true) }
        assertEquals(AutoMaintainState(true, true), settings)
        assertFalse(offer.pending.value)
    }

    @Test fun alreadyEnabledConsumesFirstSuccessWithoutOfferingLater() {
        val store = Store()
        val offer = AutoMaintainOffer(store)
        offer.release(enabled = true)
        assertFalse(offer.pending.value)
        offer.release(enabled = false)
        assertEquals(AutoMaintainOfferStage.HANDLED, store.saved)
        assertFalse(offer.pending.value)
    }

    @Test fun restorationCancelsOutstandingOfferAndStaleConfirmationCannotEnable() {
        val offer = AutoMaintainOffer(Store())
        offer.release()
        offer.manualRequested(ManualAction.RESTORE)
        offer.respond(true) { fail("A stale offer must not enable maintenance after restoration") }
        assertFalse(offer.pending.value)
    }

    @Test fun quickSettingsReleaseOffersButRestoreDoesNot() {
        val offer = AutoMaintainOffer(Store())
        offer.manualRequested(ManualAction.TOGGLE)
        offer.manualFinished(ManualAction.TOGGLE, Result.success(11), 11, AutoMaintainState())
        assertFalse(offer.pending.value)
        offer.manualFinished(ManualAction.TOGGLE, Result.success(0), 11, AutoMaintainState(false, true))
        assertTrue(offer.pending.value)
        offer.manualRequested(ManualAction.TOGGLE)
        assertFalse(offer.pending.value)
    }

    @Test fun savingOrEnablingFailureKeepsAnUnansweredOfferAvailable() {
        val store = Store()
        val offer = AutoMaintainOffer(store)
        store.failSave = true
        assertThrows(IllegalStateException::class.java) { offer.release() }
        assertFalse(offer.pending.value)
        store.failSave = false
        offer.release()
        assertThrows(IllegalStateException::class.java) { offer.respond(true) { error("Could not enable") } }
        assertTrue(offer.pending.value)
        store.failSave = true
        assertThrows(IllegalStateException::class.java) { offer.respond(false) {} }
        assertTrue(offer.pending.value)
    }
}
