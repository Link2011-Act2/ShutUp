package jp.linkserver.shutter

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ShizukuPermissionRequestTest {
    @Test fun repeatedTapsSendOneRequestOffTheCallingThreadAndWaitForTheCallToReturn() = runBlocking {
        val callingThread = Thread.currentThread()
        val entered = CompletableDeferred<Int>()
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        var grants = 0
        val request = ShizukuPermissionRequest(this, { code ->
            assertNotSame(callingThread, Thread.currentThread())
            calls.incrementAndGet()
            entered.complete(code)
            release.await()
        }, { grants++ })
        try {
            request.request()
            val code = withTimeout(3_000) { entered.await() }
            repeat(100) { request.request() }
            assertEquals(1, calls.get())
            assertTrue(request.state.value.requesting)
            request.onResult(code, granted = true)
            assertEquals(0, grants)
            assertTrue(request.state.value.requesting)
            release.countDown()
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(1, grants)
            assertNull(request.state.value.failure)
        } finally { release.countDown(); request.close() }
    }

    @Test fun denialIsVisibleAndOldOrUnrelatedResultsCannotFinishANewRequest() = runBlocking {
        val codes = Channel<Int>(Channel.UNLIMITED)
        var grants = 0
        val request = ShizukuPermissionRequest(this, { codes.trySend(it) }, { grants++ })
        try {
            request.request()
            val first = withTimeout(3_000) { codes.receive() }
            request.onResult(first + 1, granted = true)
            assertTrue(request.state.value.requesting)
            request.onResult(first, granted = false)
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(ShizukuPermissionFailure.DENIED, request.state.value.failure)
            assertEquals(0, grants)
            request.request()
            val second = withTimeout(3_000) { codes.receive() }
            assertNotEquals(first, second)
            assertNull(request.state.value.failure)
            request.onResult(first, granted = false)
            assertTrue(request.state.value.requesting)
            request.onResult(second, granted = true)
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(1, grants)
        } finally { request.close() }
    }

    @Test fun aRequestExceptionIsReportedAndAllowsAnotherAttempt() = runBlocking {
        val error = IllegalStateException("Binder unavailable")
        val calls = AtomicInteger()
        val failures = mutableListOf<Throwable?>()
        val request = ShizukuPermissionRequest(this, {
            calls.incrementAndGet()
            throw error
        }, {}, onFailure = { _, cause -> failures += cause })
        try {
            request.request()
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(ShizukuPermissionFailure.REQUEST_FAILED, request.state.value.failure)
            assertTrue(failures.single() is IllegalStateException)
            assertEquals(error.message, failures.single()?.message)
            request.request()
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(2, calls.get())
        } finally { request.close() }
    }

    @Test fun aMissingResultTimesOutAndALateGrantCanStillRecover() = runBlocking {
        val entered = CompletableDeferred<Int>()
        var grants = 0
        val request = ShizukuPermissionRequest(this, { entered.complete(it) }, { grants++ }, timeoutMillis = 100)
        try {
            request.request()
            val code = withTimeout(3_000) { entered.await() }
            val timedOut = withTimeout(3_000) { request.state.first { it.failure == ShizukuPermissionFailure.TIMED_OUT } }
            assertFalse(timedOut.requesting)
            request.onResult(code, granted = true)
            assertNull(request.state.value.failure)
            assertEquals(1, grants)
        } finally { request.close() }
    }

    @Test fun aLateDenialReplacesTheTimeoutWithTheActualDecision() = runBlocking {
        val entered = CompletableDeferred<Int>()
        val request = ShizukuPermissionRequest(this, { entered.complete(it) }, {}, timeoutMillis = 100)
        try {
            request.request()
            val code = withTimeout(3_000) { entered.await() }
            withTimeout(3_000) { request.state.first { it.failure == ShizukuPermissionFailure.TIMED_OUT } }
            request.onResult(code, granted = false)
            assertEquals(ShizukuPermissionFailure.DENIED, request.state.value.failure)
            assertFalse(request.state.value.requesting)
        } finally { request.close() }
    }

    @Test fun aBlockedBinderCallShowsATimeoutWithoutQueuingMoreCalls() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val request = ShizukuPermissionRequest(this, {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
        }, {}, timeoutMillis = 100)
        try {
            request.request()
            withTimeout(3_000) { entered.await() }
            val timedOut = withTimeout(3_000) { request.state.first { it.failure == ShizukuPermissionFailure.TIMED_OUT } }
            assertTrue(timedOut.requesting)
            repeat(100) { request.request() }
            assertEquals(1, calls.get())
            release.countDown()
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(ShizukuPermissionFailure.TIMED_OUT, request.state.value.failure)
            request.request()
            withTimeout(3_000) { request.state.first { it.failure == ShizukuPermissionFailure.TIMED_OUT } }
            assertEquals(2, calls.get())
        } finally { release.countDown(); request.close() }
    }

    @Test fun disconnectInvalidatesTheOldResultAndCloseIgnoresFurtherResults() = runBlocking {
        val entered = CompletableDeferred<Int>()
        val release = CountDownLatch(1)
        var grants = 0
        val request = ShizukuPermissionRequest(this, {
            entered.complete(it)
            release.await()
        }, { grants++ })
        try {
            request.request()
            val code = withTimeout(3_000) { entered.await() }
            request.disconnected()
            assertEquals(ShizukuPermissionFailure.REQUEST_FAILED, request.state.value.failure)
            request.onResult(code, granted = true)
            release.countDown()
            withTimeout(3_000) { request.state.first { !it.requesting } }
            assertEquals(0, grants)
            request.close()
            request.onResult(code, granted = true)
            request.request()
            assertFalse(request.state.value.requesting)
            assertEquals(0, grants)
        } finally { release.countDown(); request.close() }
    }
}
