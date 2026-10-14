package jp.linkserver.shutter

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ShizukuPermissionFailure { DENIED, REQUEST_FAILED, TIMED_OUT }

internal data class ShizukuPermissionState(
    val requesting: Boolean = false,
    val failure: ShizukuPermissionFailure? = null
)

/** Called on the scope's main thread; only the synchronous Binder call runs on IO. */
internal class ShizukuPermissionRequest(
    private val scope: CoroutineScope,
    private val requestPermission: (Int) -> Unit,
    private val onGranted: () -> Unit,
    private val timeoutMillis: Long = 15_000,
    private val onFailure: (ShizukuPermissionFailure, Throwable?) -> Unit = { _, _ -> }
) {
    private val mutableState = MutableStateFlow(ShizukuPermissionState())
    val state = mutableState.asStateFlow()
    private var requestCode = 0
    private var waiting = false
    private var callInFlight = false
    private var result: Boolean? = null
    private var timeout: Job? = null
    private var closed = false

    fun request() {
        if (closed || waiting || callInFlight) return
        val code = nextRequestCode.getAndIncrement()
        requestCode = code
        waiting = true
        callInFlight = true
        result = null
        mutableState.value = ShizukuPermissionState(requesting = true)
        timeout = scope.launch {
            delay(timeoutMillis)
            if (!closed && requestCode == code && waiting) {
                waiting = false
                // Cancelling a coroutine cannot interrupt a blocked Binder transaction.
                // Keep requests disabled until that call actually returns.
                fail(ShizukuPermissionFailure.TIMED_OUT, requesting = callInFlight)
            }
        }
        scope.launch {
            val error = try {
                withContext(Dispatchers.IO) { requestPermission(code) }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                error
            } finally {
                callInFlight = false
            }
            if (closed) return@launch
            if (requestCode != code || !waiting && error != null) {
                mutableState.value = mutableState.value.copy(requesting = false)
            } else if (error != null) {
                waiting = false
                timeout?.cancel()
                fail(ShizukuPermissionFailure.REQUEST_FAILED, error = error)
            } else {
                val granted = result
                if (granted != null) finish(granted)
                else if (!waiting) mutableState.value = mutableState.value.copy(requesting = false)
            }
        }
    }

    fun onResult(code: Int, granted: Boolean) {
        if (closed || code != requestCode) return
        if (!waiting && !callInFlight && !granted &&
            mutableState.value.failure != ShizukuPermissionFailure.TIMED_OUT) return
        result = granted
        // A result can arrive before requestPermission() returns. Do not refresh
        // synchronously against the server while its original call is still pending.
        if (!callInFlight) finish(granted)
    }

    private fun finish(granted: Boolean) {
        waiting = false
        result = null
        timeout?.cancel()
        if (granted) {
            mutableState.value = ShizukuPermissionState()
            onGranted()
        } else fail(ShizukuPermissionFailure.DENIED)
    }

    private fun fail(failure: ShizukuPermissionFailure, requesting: Boolean = false,
        error: Throwable? = null) {
        mutableState.value = ShizukuPermissionState(requesting, failure)
        onFailure(failure, error)
    }

    fun disconnected() {
        if (!waiting && !callInFlight) return
        requestCode = nextRequestCode.getAndIncrement()
        waiting = false
        result = null
        timeout?.cancel()
        fail(ShizukuPermissionFailure.REQUEST_FAILED, requesting = callInFlight)
    }

    fun clearFailure() {
        if (!waiting && !callInFlight) mutableState.value = ShizukuPermissionState()
    }

    fun close() {
        closed = true
        timeout?.cancel()
    }

    private companion object {
        val nextRequestCode = AtomicInteger(1)
    }
}
