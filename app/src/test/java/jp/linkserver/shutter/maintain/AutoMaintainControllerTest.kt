package jp.linkserver.shutter.maintain

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AutoMaintainControllerTest {
    private class Store(var saved: AutoMaintainState = AutoMaintainState()) : AutoMaintainStore {
        var failSave = false
        override fun load() = saved
        override fun save(state: AutoMaintainState) { check(!failSave); saved = state }
    }
    private class Policy(var value: Int = 11) : AudioPolicyConnection {
        val writes = mutableListOf<Int>()
        var reads = 0
        var beforeRead: (() -> Unit)? = null
        override fun read(): Int { reads++; val before = value; beforeRead?.invoke(); return before }
        override fun write(value: Int) { writes += value; this.value = value }
    }
    private class Fixture(initial: AutoMaintainState = AutoMaintainState(), value: Int = 11) {
        val store = Store(initial)
        val policy = Policy(value)
        var available = true
        var connects = 0
        val controller = AutoMaintainController(store, AudioPolicyConnector {
            connects++; if (available) policy else null
        })
        suspend fun manual(action: ManualAction, default: Int = 11) =
            controller.performManual(controller.requestManual(action), default)
    }

    @Test fun defaultsAreOffAndNeitherEventsNorWorkersConnect() = runBlocking {
        val f = Fixture()
        assertEquals(AutoMaintainState(), f.controller.state.value)
        repeat(2) { assertEquals(MaintainResult.INACTIVE, f.controller.maintain()) }
        assertEquals(0, f.connects)
        assertTrue(f.policy.writes.isEmpty())
    }

    @Test fun unknownDeviceDefaultBlocksRestorationAndToggleWithoutWriting() = runBlocking {
        for (action in listOf(ManualAction.RESTORE, ManualAction.TOGGLE)) {
            val f = Fixture(AutoMaintainState(true, true), 0)
            val result = runCatching {
                f.controller.performManual(f.controller.requestManual(action), null)
            }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(f.policy.writes.isEmpty())
            assertEquals(0, f.connects)
            assertFalse(f.controller.state.value.armed)
            assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
            // A failed request must also clear pending intent so a retry can succeed.
            assertEquals(11, f.manual(ManualAction.RESTORE, 11))
            assertEquals(listOf(11), f.policy.writes)
        }
    }

    @Test fun explicitReleaseCanProceedWhenTheDeviceDefaultIsUnknown() = runBlocking {
        val f = Fixture(value = 11)
        assertEquals(0, f.controller.performManual(f.controller.requestManual(ManualAction.RELEASE), null))
        assertEquals(listOf(0), f.policy.writes)
    }
    @Test fun enablingArmsAndRestoresEnforcementExactlyOnce() = runBlocking {
        val f = Fixture()
        f.controller.setEnabled(true)
        assertEquals(AutoMaintainState(true, true), f.store.saved)
        assertEquals(MaintainResult.RESTORED, f.controller.maintain())
        assertEquals(MaintainResult.ALREADY_RELEASED, f.controller.maintain())
        assertEquals(listOf(0), f.policy.writes)
    }
    @Test fun alreadyReleasedNeverWritesAndUnknownValuesAreLeftAlone() = runBlocking {
        for (value in listOf(0, 3, 99)) {
            val f = Fixture(AutoMaintainState(true, true), value)
            assertEquals(if (value == 0) MaintainResult.ALREADY_RELEASED else MaintainResult.UNKNOWN,
                f.controller.maintain())
            assertTrue(f.policy.writes.isEmpty())
            assertEquals(value, f.policy.value)
        }
    }
    @Test fun manualRestoreKeepsToggleOnAndPausesAllAutomaticTriggers() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        f.manual(ManualAction.RESTORE)
        assertEquals(AutoMaintainState(true, false), f.store.saved)
        assertEquals(11, f.policy.value)
        val priorReads = f.policy.reads
        repeat(3) { assertEquals(MaintainResult.INACTIVE, f.controller.maintain()) }
        assertEquals(priorReads, f.policy.reads)
        assertEquals(listOf(11), f.policy.writes)
    }
    @Test fun manualReleaseResumesWhileAnOffToggleRemainsOff() = runBlocking {
        for (enabled in listOf(false, true)) {
            val f = Fixture(AutoMaintainState(enabled, false))
            f.manual(ManualAction.RELEASE)
            assertEquals(AutoMaintainState(enabled, true), f.store.saved)
            f.policy.value = 11
            assertEquals(if (enabled) MaintainResult.RESTORED else MaintainResult.INACTIVE, f.controller.maintain())
        }
    }
    @Test fun disablingDoesNotRestoreAndOffOnRearmsAPausedSetting() = runBlocking {
        val f = Fixture(AutoMaintainState(true, false), 0)
        f.controller.setEnabled(false)
        assertEquals(0, f.policy.value)
        assertTrue(f.policy.writes.isEmpty())
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        f.controller.setEnabled(true)
        assertEquals(AutoMaintainState(true, true), f.store.saved)
        f.policy.value = 11
        f.controller.maintain()
        assertEquals(listOf(0), f.policy.writes)
    }
    @Test fun restartRetainsBothEnabledAndPausedFlags() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        f.manual(ManualAction.RESTORE)
        val restarted = AutoMaintainController(f.store, AudioPolicyConnector { f.policy })
        assertEquals(AutoMaintainState(true, false), restarted.state.value)
        assertEquals(MaintainResult.INACTIVE, restarted.maintain())
        assertEquals(11, f.policy.value)
    }
    @Test fun unavailableAndUnpermittedShizukuDoNotWriteOrChangeIntent() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true))
        f.available = false
        assertEquals(MaintainResult.UNAVAILABLE, f.controller.maintain())
        assertEquals(AutoMaintainState(true, true), f.store.saved)
        assertTrue(f.policy.writes.isEmpty())
        // Manual restoration still durably pauses before trying to connect.
        try { f.manual(ManualAction.RESTORE); fail() } catch (_: IllegalStateException) {}
        assertEquals(AutoMaintainState(true, false), f.store.saved)
    }
    @Test fun duplicateWorkerAndBinderEventsCoalesceDuringConnection() = runBlocking {
        val connected = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val policy = Policy()
        var connects = 0
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            connects++; connected.complete(Unit); gate.await(); policy
        })
        val first = async { controller.maintain() }
        connected.await()
        repeat(4) { assertEquals(MaintainResult.BUSY, controller.maintain()) }
        gate.complete(Unit)
        assertEquals(MaintainResult.RESTORED, first.await())
        assertEquals(1, connects)
        assertEquals(listOf(0), policy.writes)
    }
    @Test fun restoreCancelsAWorkersConnectionAndWinsWithoutWaitingForItsTimeout() = runBlocking {
        val connected = CompletableDeferred<Unit>()
        var cancelled = false
        var connects = 0
        val policy = Policy(0)
        val store = Store(AutoMaintainState(true, true))
        val controller = AutoMaintainController(store, AudioPolicyConnector {
            if (++connects == 1) {
                connected.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
            policy
        })
        val worker = async { controller.maintain() }
        connected.await()
        val restore = controller.requestManual(ManualAction.RESTORE)
        assertEquals(MaintainResult.SUPERSEDED, withTimeout(1000) { worker.await() })
        assertTrue(cancelled)
        controller.performManual(restore, 11)
        assertEquals(AutoMaintainState(true, false), store.saved)
        assertEquals(listOf(11), policy.writes)
    }
    @Test fun offDuringWorkerConnectionCancelsItAndLeavesThePolicyAlone() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val policy = Policy()
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            entered.complete(Unit); awaitCancellation()
        })
        val worker = async { controller.maintain() }
        entered.await()
        controller.setEnabled(false)
        assertEquals(MaintainResult.SUPERSEDED, worker.await())
        assertEquals(11, policy.value)
        assertTrue(policy.writes.isEmpty())
    }
    @Test fun restoreAfterAnAutomaticReadPreventsThePendingWrite() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true))
        val readStarted = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        f.policy.beforeRead = {
            readStarted.countDown()
            check(finishRead.await(3, TimeUnit.SECONDS))
        }
        val worker = async(Dispatchers.IO) { f.controller.maintain() }
        assertTrue(readStarted.await(3, TimeUnit.SECONDS))
        val restore = f.controller.requestManual(ManualAction.RESTORE)
        finishRead.countDown()
        assertEquals(MaintainResult.SUPERSEDED, worker.await())
        f.policy.beforeRead = null
        f.controller.performManual(restore, 11)
        assertTrue(f.policy.writes.isEmpty())
        assertEquals(11, f.policy.value)
    }
    @Test fun workerCancellationRemovesItsConnectionAndCannotWriteLater() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        val policy = Policy()
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned = true }
        })
        val worker = launch { controller.maintain() }
        entered.await()
        worker.cancelAndJoin()
        assertTrue(cleaned)
        assertTrue(policy.writes.isEmpty())
        // Mutex released after all connection work is finished.
        assertEquals(MaintainResult.INACTIVE, controller.apply { setEnabled(false) }.maintain())
    }
    @Test fun queuedOldManualReleaseCannotOverrideANewerRestoration() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        val oldRelease = f.controller.requestManual(ManualAction.RELEASE)
        val restore = f.controller.requestManual(ManualAction.RESTORE)
        f.controller.performManual(restore, 11)
        try { f.controller.performManual(oldRelease, 11); fail() } catch (_: SupersededPolicyOperation) {}
        assertEquals(11, f.policy.value)
        assertEquals(AutoMaintainState(true, false), f.store.saved)
    }
    @Test fun quickSettingsToggleRestorationAndReleaseUseTheSameIntentRules() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        f.manual(ManualAction.TOGGLE)
        assertEquals(AutoMaintainState(true, false), f.store.saved)
        assertEquals(11, f.policy.value)
        f.manual(ManualAction.TOGGLE)
        assertEquals(AutoMaintainState(true, true), f.store.saved)
        assertEquals(0, f.policy.value)
    }
    @Test fun defaultZeroRestorationPausesEvenThoughTheTargetEqualsRelease() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        f.manual(ManualAction.RESTORE, default = 0)
        assertEquals(AutoMaintainState(true, false), f.store.saved)
        assertTrue(f.policy.writes.isEmpty())
        f.policy.value = 11
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        f.manual(ManualAction.RELEASE, default = 0)
        assertEquals(AutoMaintainState(true, true), f.store.saved)
        assertEquals(listOf(0), f.policy.writes)
    }
    @Test fun failedPersistenceFailsClosedBeforeAnyPolicyWrite() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        f.store.failSave = true
        try { f.controller.requestManual(ManualAction.RESTORE); fail() } catch (_: IllegalStateException) {}
        assertFalse(f.controller.state.value.active)
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        assertTrue(f.policy.writes.isEmpty())
    }

    @Test fun aConnectionEventWaitsForAnInFlightUiQueryInsteadOfLosingRecovery() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val policy = Policy()
        var connects = 0
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            if (++connects == 1) { entered.complete(Unit); gate.await() }
            policy
        })
        val query = async { controller.query() }
        entered.await()
        val event = async { controller.maintain() }
        yield()
        gate.complete(Unit)
        assertEquals(11, query.await())
        assertEquals(MaintainResult.RESTORED, event.await())
        assertEquals(listOf(0), policy.writes)
    }

    @Test fun reEnablingBeforeTheOldCheckUnwindsStillPerformsANewCheck() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val policy = Policy()
        var connects = 0
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            if (++connects == 1) { entered.complete(Unit); awaitCancellation() }
            policy
        })
        val old = async { controller.maintain() }
        entered.await()
        controller.setEnabled(false)
        controller.setEnabled(true)
        val fresh = async { controller.maintain() }
        assertEquals(MaintainResult.SUPERSEDED, old.await())
        assertEquals(MaintainResult.RESTORED, fresh.await())
        assertEquals(listOf(0), policy.writes)
    }

    @Test fun switchingOffDoesNotDiscardAnAlreadyRequestedManualRestoration() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true), 0)
        val restore = f.controller.requestManual(ManualAction.RESTORE)
        f.controller.setEnabled(false)
        f.controller.performManual(restore, 11)
        assertEquals(11, f.policy.value)
        assertEquals(AutoMaintainState(false, false), f.store.saved)
    }

    @Test fun explicitManualRestorationStillWinsIfToggleChangesWhileConnecting() = runBlocking {
        val f = Fixture(AutoMaintainState(false, true), 0)
        val restore = f.controller.requestManual(ManualAction.RESTORE)
        f.controller.setEnabled(true)
        f.controller.performManual(restore, 11)
        assertEquals(AutoMaintainState(true, false), f.store.saved)
        assertEquals(MaintainResult.INACTIVE, f.controller.maintain())
        assertEquals(11, f.policy.value)
    }

    @Test fun aConnectionTimeoutCleansTheWaitAndDefersWithoutWriting() = runBlocking {
        var cleaned = false
        val controller = AutoMaintainController(Store(AutoMaintainState(true, true)), AudioPolicyConnector {
            try { awaitCancellation() } finally { cleaned = true }
        }, connectionTimeoutMs = 20)
        assertEquals(MaintainResult.UNAVAILABLE, controller.maintain())
        assertTrue(cleaned)
    }

    @Test fun offImmediatelyAfterReadPreventsAnAutomaticWrite() = runBlocking {
        val f = Fixture(AutoMaintainState(true, true))
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        f.policy.beforeRead = { entered.countDown(); check(finish.await(3, TimeUnit.SECONDS)) }
        val worker = async(Dispatchers.IO) { f.controller.maintain() }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        f.controller.setEnabled(false)
        finish.countDown()
        assertEquals(MaintainResult.SUPERSEDED, worker.await())
        assertTrue(f.policy.writes.isEmpty())
        assertEquals(11, f.policy.value)
    }
}
