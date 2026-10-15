package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SamsungUserReleaseGuardTest {
    private class ConsentStore : SamsungUserConsentStore {
        var saved = SamsungUserConsent()
        override fun load() = saved
        override fun save(consent: SamsungUserConsent) { saved = consent }
    }
    private class Store(var saved: AutoMaintainState) : AutoMaintainStore {
        override fun load() = saved
        override fun save(state: AutoMaintainState) { saved = state }
    }
    private class Policy(var value: Int) : AudioPolicyConnection {
        val writes = mutableListOf<Int>()
        var beforeRead: (() -> Unit)? = null
        override fun read(): Int { beforeRead?.invoke(); return value }
        override fun write(value: Int) { writes += value; this.value = value }
    }
    private class Fixture(initial: AutoMaintainState = AutoMaintainState(), value: Int = 11,
        environment: AppUserEnvironment = AppUserEnvironment("samsung", 95)) {
        val consent = ConsentStore()
        val warning = SamsungUserWarning(environment, consent)
        val store = Store(initial)
        val policy = Policy(value)
        var connects = 0
        val controller = AutoMaintainController(store, AudioPolicyConnector { connects++; policy }, releaseAuthorization = warning)
        suspend fun manual(action: ManualAction, permit: ReleasePermit? = null, default: Int = 11) =
            controller.performManual(controller.requestManual(action, permit), default)
    }

    @Test fun anUnconfirmedReleaseIsBlockedBeforeSavingIntentConnectingOrWriting() {
        val f = Fixture(AutoMaintainState(false, true))
        assertThrows(SamsungUserConfirmationRequired::class.java) { f.controller.requestManual(ManualAction.RELEASE) }
        assertEquals(AutoMaintainState(false, true), f.store.saved)
        assertEquals(0, f.connects)
        assertTrue(f.policy.writes.isEmpty())
    }

    @Test fun oldEnabledStateCannotRunWorkersAndConfirmationAloneDoesNotResumeIt() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true))
        assertEquals(AutoMaintainState(false, true), f.controller.state.value)
        repeat(3) { assertEquals(MaintainResult.INACTIVE, f.controller.maintain()) }
        assertEquals(0, f.connects)
        f.warning.confirm(remember = true)
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        assertThrows(SamsungUserConfirmationRequired::class.java) {
            Fixture().controller.setEnabled(true)
        }
        f.controller.setEnabled(true)
        assertEquals(MaintainResult.RESTORED, f.controller.maintain())
        assertEquals(listOf(0), f.policy.writes)
        val reloaded = AutoMaintainController(f.store, AudioPolicyConnector { f.policy },
            releaseAuthorization = SamsungUserWarning(AppUserEnvironment("samsung", 95), f.consent))
        f.policy.value = 11
        assertEquals(MaintainResult.RESTORED, reloaded.maintain())
    }

    @Test fun qsToggleCannotReleaseWithoutConsentButCanRestoreWithoutIt() = runBlocking {
        val blocked = Fixture(AutoMaintainState(false, true))
        val request = blocked.controller.requestManual(ManualAction.TOGGLE)
        assertTrue(blocked.controller.state.value.armed)
        val error = runCatching { blocked.controller.performManual(request, 11) }.exceptionOrNull()
        assertTrue(error is SamsungUserConfirmationRequired)
        assertTrue(blocked.policy.writes.isEmpty())
        assertTrue(blocked.controller.state.value.armed)
        val restore = Fixture(AutoMaintainState(true, true), value = 0)
        assertEquals(11, restore.manual(ManualAction.TOGGLE))
        assertEquals(listOf(11), restore.policy.writes)
        assertFalse(restore.controller.state.value.armed)
        assertTrue(restore.warning.confirmationRequired)
    }

    @Test fun continueWithRememberOffAuthorizesExactlyOneManualRequest() = runBlocking {
        val f = Fixture()
        val permit = f.warning.confirm(remember = false)
        assertEquals(0, f.manual(ManualAction.RELEASE, permit))
        assertEquals(listOf(0), f.policy.writes)
        f.policy.value = 11
        assertThrows(SamsungUserConfirmationRequired::class.java) { f.controller.requestManual(ManualAction.RELEASE, permit) }
        assertThrows(SamsungUserConfirmationRequired::class.java) { f.controller.requestManual(ManualAction.RELEASE) }
        assertEquals(0, f.manual(ManualAction.RELEASE, f.warning.confirm(remember = false)))
        assertEquals(listOf(0, 0), f.policy.writes)
    }

    @Test fun rememberedConsentAllowsLaterManualReleasesAndRestoresRemainUnrestricted() = runBlocking {
        val f = Fixture()
        f.warning.confirm(remember = true)
        assertEquals(0, f.manual(ManualAction.RELEASE))
        assertEquals(11, f.manual(ManualAction.RESTORE))
        assertEquals(0, f.manual(ManualAction.RELEASE))
        assertEquals(listOf(0, 11, 0), f.policy.writes)
        val unconfirmed = Fixture(value = 11)
        assertEquals(0, unconfirmed.manual(ManualAction.RESTORE, default = 0))
        assertTrue(unconfirmed.warning.confirmationRequired)
    }

    @Test fun cscRestorationRecordCanBeRestoredWithoutConfirmingTheWarning() = runBlocking {
        var csc = 0
        var savedJournal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
        val writes = mutableListOf<Int?>()
        val store = object : ShutterChangeStore {
            override fun load() = savedJournal
            override fun save(journal: ShutterJournal) { savedJournal = journal }
        }
        val backend = ShutterBackend(object : ShutterDevice {
            override fun readAudio() = 11
            override fun writeAudio(value: Int) { fail("Restoration must use the recorded CSC method") }
            override fun readCsc() = csc.toString()
            override fun writeCsc(value: Int?) { writes += value; csc = value!! }
            override fun canWriteCsc() = true
        }, store, samsung = true, deviceDefault = 11)
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 95), ConsentStore())
        val controller = AutoMaintainController(Store(AutoMaintainState()), AudioPolicyConnector {
            BackendPolicyConnection(backend, store) {}
        }, releaseAuthorization = warning)
        assertThrows(SamsungUserConfirmationRequired::class.java) { controller.requestManual(ManualAction.RELEASE) }
        assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 1), savedJournal.change)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        assertEquals(listOf(1), writes)
        assertNull(savedJournal.change)
        assertTrue(warning.confirmationRequired)
    }

    @Test fun newerRestorationStillSupersedesAnAlreadyConfirmedPendingRelease() = runBlocking {
        val f = Fixture()
        val readStarted = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        f.policy.beforeRead = { readStarted.countDown(); check(finishRead.await(3, TimeUnit.SECONDS)) }
        val request = f.controller.requestManual(ManualAction.RELEASE, f.warning.confirm(remember = false))
        val pending = async(Dispatchers.IO) { runCatching { f.controller.performManual(request, 11) } }
        try {
            assertTrue(readStarted.await(3, TimeUnit.SECONDS))
            val restore = f.controller.requestManual(ManualAction.RESTORE)
            finishRead.countDown()
            assertTrue(pending.await().exceptionOrNull() is SupersededPolicyOperation)
            f.policy.beforeRead = null
            f.controller.performManual(restore, 11)
            assertTrue(f.policy.writes.isEmpty())
        } finally { finishRead.countDown() }
    }

    @Test fun enteringSimulationDisablesOldMaintenanceAndRechecksPendingReleases() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), environment = AppUserEnvironment("Google", 0))
        val pending = f.controller.requestManual(ManualAction.RELEASE)
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 1), ConsentStore())
        f.controller.updateReleaseAuthorization(warning)
        assertFalse(f.controller.state.value.enabled)
        assertFalse(f.store.saved.enabled)
        assertTrue(f.controller.state.value.armed)
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        assertTrue(runCatching { f.controller.performManual(pending, 11) }.exceptionOrNull() is SamsungUserConfirmationRequired)
        assertTrue(f.policy.writes.isEmpty())
        assertEquals(11, f.manual(ManualAction.RESTORE))
    }

    @Test fun resetSimulationRejectsOldPermitsButKeepsPendingRestoration() = runBlocking {
        val f = Fixture(value = 0)
        val oldPermit = f.warning.confirm(remember = false)
        val restore = f.controller.requestManual(ManualAction.RESTORE)
        val reset = SamsungUserWarning(AppUserEnvironment("samsung", 95), ConsentStore())
        f.controller.updateReleaseAuthorization(reset)
        assertThrows(SamsungUserConfirmationRequired::class.java) {
            f.controller.requestManual(ManualAction.RELEASE, oldPermit)
        }
        assertEquals(11, f.controller.performManual(restore, 11))
        assertEquals(listOf(11), f.policy.writes)
        f.controller.updateReleaseAuthorization(UnrestrictedReleaseAuthorization)
        assertEquals(0, f.manual(ManualAction.RELEASE))
        assertFalse(f.controller.state.value.enabled)
    }

    @Test fun mainSamsungUserAndOtherManufacturersPreserveExistingAutomaticAndManualBehavior() = runBlocking {
        for (environment in listOf(AppUserEnvironment("samsung", 0), AppUserEnvironment("Google", 95))) {
            val f = Fixture(AutoMaintainState(true, true), environment = environment)
            assertEquals(MaintainResult.RESTORED, f.controller.maintain())
            assertEquals(11, f.manual(ManualAction.RESTORE))
            assertEquals(0, f.manual(ManualAction.RELEASE))
            assertEquals(listOf(0, 11, 0), f.policy.writes)
        }
    }
}
