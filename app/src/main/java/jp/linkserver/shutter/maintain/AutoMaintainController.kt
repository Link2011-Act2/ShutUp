package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.ShutterChangeStore
import jp.linkserver.shutter.ShutterMethodChangeBlocked
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.ReleaseAuthorization
import jp.linkserver.shutter.ReleasePermit
import jp.linkserver.shutter.UnrestrictedReleaseAuthorization
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

internal data class AutoMaintainState(val enabled: Boolean = false, val armed: Boolean = false) {
    val active get() = enabled && armed
}

internal interface AutoMaintainStore {
    fun load(): AutoMaintainState
    /** Must durably save both flags together before returning. */
    fun save(state: AutoMaintainState)
}

internal interface AudioPolicyConnection {
    val canRelease: Boolean get() = true
    val releaseWouldChangeSetting: Boolean get() = true
    fun read(): Int
    fun write(value: Int)
    fun baseline(deviceDefault: Int?): Int? = deviceDefault
    fun shouldRestore(current: Int, deviceDefault: Int?): Boolean = current != deviceDefault
    fun release(beforeWrite: () -> Unit = {}): Int {
        beforeWrite()
        write(ForceUse.FORCE_NONE)
        return read()
    }
    fun restore(deviceDefault: Int?, beforeWrite: () -> Unit = {}): Int {
        beforeWrite()
        write(checkNotNull(deviceDefault))
        return read()
    }
}

internal fun interface AudioPolicyConnector {
    /** Cancellable; no policy operation may be hidden inside the connection callback. */
    suspend fun connect(): AudioPolicyConnection?
    val storedRestoration: Boolean get() = false
}

internal enum class ManualAction { RELEASE, RESTORE, TOGGLE }
internal class ManualRequest(val generation: Long, val action: ManualAction, val releasePermit: ReleasePermit? = null)
internal enum class MaintainResult { INACTIVE, BUSY, UNAVAILABLE, ALREADY_RELEASED, RESTORED, UNKNOWN, SUPERSEDED }
internal class SupersededPolicyOperation : IllegalStateException("A newer manual operation superseded this request")

/** One process-wide owner of preferences and all audio-policy reads/writes. */
internal class AutoMaintainController(
    private val store: AutoMaintainStore,
    private val connector: AudioPolicyConnector,
    private val connectionTimeoutMs: Long = 10_000,
    private var releaseAuthorization: ReleaseAuthorization = UnrestrictedReleaseAuthorization
) {
    private val intentLock = Any()
    private val operations = Mutex()
    private var generation = 0L
    private var manualGeneration = 0L
    private var pendingManual: Long? = null
    private var activeAutomatic: Deferred<MaintainResult>? = null
    private val mutableState = MutableStateFlow(store.load().let {
        // Keep armed restoration evidence, but never resume an old enabled flag
        // until this environment has explicitly authorized automatic releases.
        if (releaseAuthorization.automaticAllowed) it else it.copy(enabled = false)
    })
    val state = mutableState.asStateFlow()
    private val mutableCurrent = MutableStateFlow<Int?>(null)
    val current = mutableCurrent.asStateFlow()

    /** Serialize a warning simulation change with the final check before every device write. */
    fun updateReleaseAuthorization(authorization: ReleaseAuthorization) = synchronized(intentLock) {
        releaseAuthorization = authorization
        updateIntent(if (authorization.automaticAllowed) state.value else state.value.copy(enabled = false))
    }

    /** A mode change cannot race a manual request or a background device write. */
    suspend fun updateControlMethod(journal: ShutterChangeStore, resetMaintenance: Boolean = false, update: () -> Unit) = operations.withLock {
        synchronized(intentLock) {
            check(pendingManual == null) { "A shutter operation is pending" }
            if (journal.load().change != null) throw ShutterMethodChangeBlocked()
            // Pause the old store before swapping real/emulated persistence. A restart
            // or channel change must never inherit a simulated automatic release.
            if (resetMaintenance) updateIntent(AutoMaintainState())
            update() // Durably commit the selection under the same locks.
            updateIntent(if (resetMaintenance) AutoMaintainState() else state.value.copy(armed = false))
            mutableCurrent.value = null
        }
    }

    fun setEnabled(enabled: Boolean) = synchronized(intentLock) {
        if (state.value.enabled == enabled) return@synchronized
        if (enabled) releaseAuthorization.enableAutomatic()
        updateIntent(if (enabled) AutoMaintainState(true, true) else state.value.copy(enabled = false))
    }

    /** Record intent synchronously, before a coroutine or service connection can be delayed. */
    fun requestManual(action: ManualAction, releasePermit: ReleasePermit? = null): ManualRequest = synchronized(intentLock) {
        val request = ManualRequest(manualGeneration + 1, action, releasePermit)
        if (action == ManualAction.RELEASE) releaseAuthorization.requireManualRelease(request, releasePermit)
        val armed = when (action) {
            ManualAction.RESTORE -> false
            ManualAction.RELEASE -> true
            ManualAction.TOGGLE -> if (releaseAuthorization.confirmationRequired) state.value.armed else false
        }
        manualGeneration++
        updateIntent(state.value.copy(armed = armed))
        pendingManual = manualGeneration
        request
    }

    private fun updateIntent(next: AutoMaintainState) {
        generation++
        activeAutomatic?.cancel()
        // Fail closed in memory if durable storage fails; no write follows a failed save.
        saveState(next)
    }

    private fun saveState(next: AutoMaintainState) {
        mutableState.value = next
        try { store.save(next) }
        catch (error: Exception) { mutableState.value = next.copy(armed = false); throw error }
    }

    suspend fun performManual(request: ManualRequest, deviceDefault: Int?): Int = try {
        operations.withLock {
            synchronized(intentLock) { requireCurrent(request) }
            check(deviceDefault != null || request.action == ManualAction.RELEASE || connector.storedRestoration) {
                "The device shutter sound default is unavailable"
            }
            val connection = connection() ?: error("Shizuku is unavailable or permission is not granted")
            val context = currentCoroutineContext()
            context.ensureActive()
            val before = connection.read()
            val action = synchronized(intentLock) {
                context.ensureActive()
                requireCurrent(request)
                val action = if (request.action == ManualAction.TOGGLE) {
                    if (connection.shouldRestore(before, deviceDefault)) ManualAction.RESTORE else ManualAction.RELEASE
                } else request.action
                // A tile on an already released, non-restorable setting is a no-op.
                if (request.action == ManualAction.TOGGLE && action == ManualAction.RELEASE &&
                    !connection.releaseWouldChangeSetting && before == ForceUse.FORCE_NONE) {
                    mutableCurrent.value = before
                    return@withLock before
                }
                if (action == ManualAction.RELEASE)
                    releaseAuthorization.requireManualRelease(request, request.releasePermit)
                // An OFF operation stops only automatic work, never a pending manual restoration.
                // Reassert manual intent if the toggle changed while connecting.
                val next = state.value.copy(armed = action == ManualAction.RELEASE)
                if (next != state.value) saveState(next)
                action
            }
            val target = if (action == ManualAction.RESTORE) connection.baseline(deviceDefault) else ForceUse.FORCE_NONE
            val actual = if (connector.storedRestoration || before != target) {
                val beforeWrite = {
                    synchronized(intentLock) {
                        context.ensureActive()
                        requireCurrent(request)
                        if (action == ManualAction.RELEASE)
                            releaseAuthorization.requireManualRelease(request, request.releasePermit)
                    }
                }
                // IPC may take seconds. The operation mutex keeps device writes
                // ordered while UI intent updates remain independent of that wait.
                // A write already submitted may finish; a newer restore runs next.
                if (action == ManualAction.RESTORE) connection.restore(deviceDefault, beforeWrite)
                else connection.release(beforeWrite)
            } else before
            mutableCurrent.value = actual
            actual
        }
    } finally {
        synchronized(intentLock) { if (pendingManual == request.generation) pendingManual = null }
    }

    private fun requireCurrent(request: ManualRequest) {
        if (request.generation != manualGeneration) throw SupersededPolicyOperation()
    }

    suspend fun query(): Int = operations.withLock {
        val connection = connection() ?: error("Shizuku is unavailable or permission is not granted")
        currentCoroutineContext().ensureActive()
        connection.read().also { mutableCurrent.value = it }
    }

    suspend fun maintain(): MaintainResult = coroutineScope {
        val attempt = synchronized(intentLock) {
            if (!state.value.active || !releaseAuthorization.automaticAllowed) return@coroutineScope MaintainResult.INACTIVE
            if (pendingManual != null || activeAutomatic?.isCancelled == false) return@coroutineScope MaintainResult.BUSY
            val ticket = generation
            // Queue behind a UI read, but coalesce all duplicate automatic triggers.
            async(start = CoroutineStart.LAZY) {
                operations.withLock { maintainLocked(ticket) }
            }.also { activeAutomatic = it }
        }
        try {
            attempt.start()
            attempt.await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive() // Propagate a Worker stop, not an intent supersession.
            if (cancelled is TimeoutCancellationException) MaintainResult.UNAVAILABLE else MaintainResult.SUPERSEDED
        } finally {
            // Never release serialization while a cancelled child's synchronous Binder call is running.
            withContext(NonCancellable) { attempt.cancelAndJoin() }
            synchronized(intentLock) { if (activeAutomatic === attempt) activeAutomatic = null }
        }
    }

    private suspend fun maintainLocked(ticket: Long): MaintainResult {
        if (!eligible(ticket)) return MaintainResult.SUPERSEDED
        val connection = connection() ?: return MaintainResult.UNAVAILABLE
        val context = currentCoroutineContext()
        context.ensureActive()
        if (!eligible(ticket)) return MaintainResult.SUPERSEDED
        val before = connection.read()
        synchronized(intentLock) {
            context.ensureActive()
            if (!eligible(ticket)) return MaintainResult.SUPERSEDED
            mutableCurrent.value = before
            if (!connection.canRelease) return MaintainResult.INACTIVE
        }
        return when (before) {
            ForceUse.FORCE_NONE -> MaintainResult.ALREADY_RELEASED
            ForceUse.FORCE_SYSTEM_ENFORCED -> {
                mutableCurrent.value = connection.release {
                    synchronized(intentLock) {
                        context.ensureActive()
                        if (!eligible(ticket)) throw SupersededPolicyOperation()
                        releaseAuthorization.requireAutomaticRelease()
                    }
                }
                MaintainResult.RESTORED
            }
            else -> MaintainResult.UNKNOWN
        }
    }

    private fun eligible(ticket: Long) = synchronized(intentLock) {
        ticket == generation && state.value.active && pendingManual == null && releaseAuthorization.automaticAllowed
    }

    private suspend fun connection() = withTimeout(connectionTimeoutMs) { connector.connect() }
}
