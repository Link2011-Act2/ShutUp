package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.AudioPolicyException
import jp.linkserver.shutter.AudioPolicyFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Bounds the caller's wait without abandoning a synchronous Binder call or its mutex. */
internal class PolicyQuery(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 12_000,
    private val read: suspend () -> Int
) {
    private val lock = Any()
    private var pending: Deferred<Result<Int>>? = null

    private var generation = 0L

    /** Called after a serialized method change; an old result must not reach any waiter. */
    fun invalidate() = synchronized(lock) {
        generation++
        pending = null
    }

    suspend fun await(): Result<Int> = try {
        withTimeout(timeoutMs) { awaitCurrent() }
    } catch (timeout: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        Result.failure(AudioPolicyException(AudioPolicyFailure.QUERY_TIMEOUT, timeout))
    }

    private suspend fun awaitCurrent(): Result<Int> {
        while (true) {
            val (ticket, query) = synchronized(lock) {
                generation to (pending?.takeUnless { it.isCompleted } ?: scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    try { Result.success(read()) }
                    catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Result.failure(error) }
                }.also { pending = it })
            }
            query.start()
            val result = query.await()
            if (synchronized(lock) { ticket == generation }) return result
        }
        // The application scope owns IPC; caller cancellation never abandons its mutex.
    }
}
