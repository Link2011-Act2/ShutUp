package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.AudioPolicyException
import jp.linkserver.shutter.AudioPolicyFailure
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PolicyQueryTest {
    @Test fun concurrentChecksReceiveTheSameFailureAndTheNextCheckCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val failure = AudioPolicyException(AudioPolicyFailure.SERVICE_START_FAILED,
            IllegalStateException("Original binding failure"))
        val query = PolicyQuery(scope) {
            reads.incrementAndGet()
            gate.await()
            throw failure
        }
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { query.await() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { query.await() }
            gate.complete(Unit)
            assertSame(failure, withTimeout(2_000) { first.await() }.exceptionOrNull())
            assertSame(failure, second.await().exceptionOrNull())
            assertEquals(1, reads.get())
            assertSame(failure, query.await().exceptionOrNull())
            assertEquals(2, reads.get())
        } finally { scope.cancel() }
    }

    @Test fun aBlockedBinderReadTimesOutWithoutDuplicatingReadsOrReleasingWriteSerialization() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val reads = AtomicInteger()
        val writes = AtomicInteger()
        val currentValue = AtomicInteger(11)
        val policy = object : AudioPolicyConnection {
            override fun read(): Int {
                reads.incrementAndGet()
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                return currentValue.get()
            }
            override fun write(value: Int) { writes.incrementAndGet(); currentValue.set(value) }
        }
        val controller = AutoMaintainController(object : AutoMaintainStore {
            override fun load() = AutoMaintainState()
            override fun save(state: AutoMaintainState) = Unit
        }, AudioPolicyConnector { policy })
        val query = PolicyQuery(scope, timeoutMs = 100) { controller.query() }
        var manual: Deferred<Int>? = null
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { query.await() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val request = controller.requestManual(ManualAction.RELEASE)
            manual = scope.async { controller.performManual(request, 11) }
            val timeout = withTimeout(2_000) { first.await() }.exceptionOrNull() as AudioPolicyException
            assertEquals(AudioPolicyFailure.QUERY_TIMEOUT, timeout.failure)
            repeat(3) {
                assertEquals(AudioPolicyFailure.QUERY_TIMEOUT,
                    (query.await().exceptionOrNull() as AudioPolicyException).failure)
            }
            assertEquals(1, reads.get())
            assertEquals(0, writes.get())
            assertFalse(manual.isCompleted)
            finish.countDown()
            assertEquals(0, withTimeout(2_000) { manual.await() })
            assertEquals(1, writes.get())
            // After the blocked call really completes, future checks can perform a fresh read.
            assertEquals(0, query.await().getOrThrow())
            assertEquals(4, reads.get()) // check, manual read, write verification, fresh check
        } finally {
            finish.countDown()
            manual?.join()
            scope.cancel()
        }
    }

    @Test fun cancellingAWaitingScreenDoesNotCancelTheSharedRead() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val query = PolicyQuery(scope) {
            reads.incrementAndGet()
            entered.complete(Unit)
            gate.await()
            11
        }
        try {
            val screen = async { query.await() }
            withTimeout(2_000) { entered.await() }
            screen.cancelAndJoin()
            val replacement = async(start = CoroutineStart.UNDISPATCHED) { query.await() }
            gate.complete(Unit)
            assertEquals(11, replacement.await().getOrThrow())
            assertEquals(1, reads.get())
        } finally { scope.cancel() }
    }

    @Test fun aMethodChangeInvalidatesOldResultsWithoutCancellingAnInFlightRead() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val finishOld = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        var finishedOld = false
        val query = PolicyQuery(scope) {
            if (reads.incrementAndGet() == 1) {
                entered.complete(Unit)
                finishOld.await()
                finishedOld = true
                11
            } else 0
        }
        try {
            val screen = async(start = CoroutineStart.UNDISPATCHED) { query.await() }
            withTimeout(2_000) { entered.await() }
            query.invalidate()
            finishOld.complete(Unit)
            assertEquals(0, withTimeout(2_000) { screen.await() }.getOrThrow())
            assertTrue(finishedOld)
            assertEquals(2, reads.get())
        } finally { finishOld.complete(Unit); scope.cancel() }
    }

    @Test fun aConnectionTimeoutRetainsItsCauseInsteadOfLookingLikeAReadTimeout() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val query = PolicyQuery(scope) { withTimeout(20) { awaitCancellation() } }
        try {
            assertTrue(query.await().exceptionOrNull() is TimeoutCancellationException)
        } finally { scope.cancel() }
    }
}
