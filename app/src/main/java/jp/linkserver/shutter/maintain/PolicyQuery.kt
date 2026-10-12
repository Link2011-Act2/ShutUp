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

    suspend fun await(): Result<Int> {
        val query = synchronized(lock) {
            pending?.takeUnless { it.isCompleted } ?: scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try { Result.success(read()) }
                catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            }.also { pending = it }
        }
        query.start()
        return try {
            withTimeout(timeoutMs) { query.await() }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(AudioPolicyException(AudioPolicyFailure.QUERY_TIMEOUT, timeout))
        }
        // The application scope owns the IPC. Cancellation/timeout of a caller must
        // neither cancel it nor let a subsequent query queue another blocked read.
    }
}
