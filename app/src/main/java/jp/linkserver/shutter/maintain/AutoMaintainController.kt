package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.ForceUse
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
    fun read(): Int
    fun write(value: Int)
}

internal fun interface AudioPolicyConnector {
    /** Cancellable; no policy operation may be hidden inside the connection callback. */
    suspend fun connect(): AudioPolicyConnection?
}

internal enum class ManualAction { RELEASE, RESTORE, TOGGLE }
internal class ManualRequest(val generation: Long, val action: ManualAction)
internal enum class MaintainResult { INACTIVE, BUSY, UNAVAILABLE, ALREADY_RELEASED, RESTORED, UNKNOWN, SUPERSEDED }
internal class SupersededPolicyOperation : IllegalStateException("A newer manual operation superseded this request")

/** One process-wide owner of preferences and all audio-policy reads/writes. */
internal class AutoMaintainController(
    private val store: AutoMaintainStore,
    private val connector: AudioPolicyConnector,
    private val connectionTimeoutMs: Long = 10_000
) {
    private val intentLock = Any()
    private val operations = Mutex()
    private var generation = 0L
    private var manualGeneration = 0L
    private var pendingManual: Long? = null
    private var activeAutomatic: Deferred<MaintainResult>? = null
    private val mutableState = MutableStateFlow(store.load())
    val state = mutableState.asStateFlow()
    private val mutableCurrent = MutableStateFlow<Int?>(null)
    val current = mutableCurrent.asStateFlow()

    fun setEnabled(enabled: Boolean) = synchronized(intentLock) {
        if (state.value.enabled == enabled) return@synchronized
        updateIntent(if (enabled) AutoMaintainState(true, true) else state.value.copy(enabled = false))
    }

    /** Record intent synchronously, before a coroutine or service connection can be delayed. */
    fun requestManual(action: ManualAction): ManualRequest = synchronized(intentLock) {
        val armed = when (action) {
            ManualAction.RESTORE -> false
            ManualAction.RELEASE -> true
            ManualAction.TOGGLE -> false // Safely pause until an actual serialized read resolves intent.
        }
        manualGeneration++
        updateIntent(state.value.copy(armed = armed))
        pendingManual = manualGeneration
        ManualRequest(manualGeneration, action)
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
            check(deviceDefault != null || request.action == ManualAction.RELEASE) {
                "The device shutter sound default is unavailable"
            }
            val connection = connection() ?: error("Shizuku is unavailable or permission is not granted")
            val context = currentCoroutineContext()
            context.ensureActive()
            val before = connection.read()
            synchronized(intentLock) {
                context.ensureActive()
                requireCurrent(request)
                val action = if (request.action == ManualAction.TOGGLE) {
                    if (before != deviceDefault) ManualAction.RESTORE else ManualAction.RELEASE
                } else request.action
                // An OFF operation stops only automatic work, never a pending manual restoration.
                // Reassert manual intent if the toggle changed while connecting.
                val next = state.value.copy(armed = action == ManualAction.RELEASE)
                if (next != state.value) saveState(next)
                val target = if (action == ManualAction.RESTORE) checkNotNull(deviceDefault) else ForceUse.FORCE_NONE
                if (before != target) connection.write(target)
                mutableCurrent.value = target
                target
            }
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
            if (!state.value.active) return@coroutineScope MaintainResult.INACTIVE
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
        return synchronized(intentLock) {
            context.ensureActive()
            if (!eligible(ticket)) return@synchronized MaintainResult.SUPERSEDED
            mutableCurrent.value = before
            when (before) {
                ForceUse.FORCE_NONE -> MaintainResult.ALREADY_RELEASED
                ForceUse.FORCE_SYSTEM_ENFORCED -> {
                    // Intent check and synchronous IPC write share one lock.
                    connection.write(ForceUse.FORCE_NONE)
                    mutableCurrent.value = ForceUse.FORCE_NONE
                    MaintainResult.RESTORED
                }
                else -> MaintainResult.UNKNOWN
            }
        }
    }

    private fun eligible(ticket: Long) = synchronized(intentLock) {
        ticket == generation && state.value.active && pendingManual == null
    }

    private suspend fun connection() = withTimeout(connectionTimeoutMs) { connector.connect() }
}
