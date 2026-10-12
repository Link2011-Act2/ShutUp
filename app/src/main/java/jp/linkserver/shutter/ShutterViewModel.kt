package jp.linkserver.shutter

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.shutter.maintain.AutoMaintainRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

data class ShutterUiState(
    val status: ShutterSound.Status = ShutterSound.Status.NOT_RUNNING,
    val current: Int? = null,
    val busy: Boolean = false,
    val error: String = "",
    val checking: Boolean = false
) {
    val working get() = busy || checking
    val canRelease get() = !working && status == ShutterSound.Status.READY && current != null && current != ShutterTileService.released()
    val canRestore get() = !working && status == ShutterSound.Status.READY && current != null &&
        ForceUse.deviceDefault()?.let { current != it } == true
    val alreadyNone get() = !working && current == ForceUse.FORCE_NONE && ForceUse.deviceDefault() == ForceUse.FORCE_NONE
}

/** Keeps in-flight Shizuku operations alive through configuration and locale changes. */
class ShutterViewModel private constructor(
    application: Application,
    private val statusSource: () -> ShutterSound.Status,
    private val querySource: ((Result<Int>) -> Unit) -> Unit,
    private val listenToShizuku: Boolean
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ShutterSound::status,
        { ShutterSound.query(application, it) }, true)

    internal constructor(application: Application, statusSource: () -> ShutterSound.Status,
        querySource: ((Result<Int>) -> Unit) -> Unit) : this(application, statusSource, querySource, false)

    private val mutableState = MutableStateFlow(ShutterUiState())
    val state = mutableState.asStateFlow()
    private var cleared = false
    private var queryGeneration = 0
    private val maintenance = AutoMaintainRuntime.get(application)
    internal val autoMaintainState = maintenance.controller.state
    internal val autoMaintainOfferPending = maintenance.offer.pending
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener { refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        viewModelScope.launch {
            maintenance.controller.current.collect { current ->
                if (current != null && !state.value.working && state.value.status == ShutterSound.Status.READY)
                    mutableState.update { it.copy(current = current, error = "") }
            }
        }
        if (listenToShizuku) {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(permission)
        }
    }

    fun authorize() {
        if (state.value.status == ShutterSound.Status.NOT_AUTHORIZED) Shizuku.requestPermission(1)
    }

    fun refresh() {
        if (cleared) return
        val status = statusSource()
        mutableState.update { it.copy(status = status) }
        if (status != ShutterSound.Status.READY) {
            ++queryGeneration
            mutableState.update { it.copy(current = null, checking = false, error = "") }
            return
        }
        // Sticky Binder notifications, onResume and button taps share this check.
        // A duplicate must not invalidate the callback that will report its failure.
        if (state.value.working) return
        val generation = ++queryGeneration
        mutableState.update { it.copy(checking = true, error = "") }
        querySource query@{ result ->
            if (cleared || generation != queryGeneration || state.value.busy) return@query
            result.onSuccess { config ->
                mutableState.update { it.copy(current = maintenance.controller.current.value ?: config,
                    checking = false, error = "") }
            }
                .onFailure { error ->
                    Log.e("ShutterSound", "Could not read FOR_SYSTEM", error)
                    mutableState.update { it.copy(current = null, checking = false,
                        error = getApplication<Application>().audioPolicyErrorMessage(error)) }
                }
        }
    }

    fun release() = apply(restore = false)
    fun restore() = apply(restore = true)

    internal fun setAutoMaintainEnabled(enabled: Boolean) {
        runCatching { maintenance.setEnabled(enabled) }.onFailure {
            Log.e("AutoMaintain", "Could not save settings", it)
            Toast.makeText(getApplication(), R.string.auto_maintain_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    internal fun respondToAutoMaintainOffer(maintain: Boolean) {
        runCatching { maintenance.respondToOffer(maintain) }.onFailure {
            Log.e("AutoMaintain", "Could not save first-release choice", it)
            Toast.makeText(getApplication(), R.string.auto_maintain_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun apply(restore: Boolean) {
        if (state.value.working || state.value.status != ShutterSound.Status.READY) return
        val config = if (restore) ForceUse.deviceDefault() else ForceUse.FORCE_NONE
        if (config == null) {
            mutableState.update { it.copy(error = getApplication<Application>().getString(R.string.device_default_unavailable)) }
            return
        }
        val before = state.value.current
        ++queryGeneration
        mutableState.update { it.copy(busy = true, error = "") }
        val operation = if (restore) ShutterSound::restore else ShutterSound::release
        operation(getApplication()) callback@{ result ->
            if (cleared) return@callback
            mutableState.update { it.copy(busy = false) }
            val context = getApplication<Application>()
            result.onSuccess {
                Log.i("ShutterSound", "FOR_SYSTEM $before -> $config")
                val message = when (config) {
                    ForceUse.FORCE_NONE -> context.getString(R.string.result_released)
                    ForceUse.FORCE_SYSTEM_ENFORCED -> context.getString(R.string.result_enforced)
                    else -> context.getString(R.string.result_changed, label(before), label(config))
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                refresh()
            }.onFailure { error ->
                Log.e("ShutterSound", "Could not set FOR_SYSTEM to $config", error)
                Toast.makeText(context, R.string.result_failed, Toast.LENGTH_LONG).show()
                mutableState.update { it.copy(current = null, error = when (error) {
                    is AudioPolicyException -> context.audioPolicyErrorMessage(error)
                    is SecurityException -> context.getString(R.string.audio_routing_permission_denied)
                    else -> context.getString(R.string.uninstall_reminder_error)
                }) }
            }
        }
    }

    private fun label(config: Int?): String = when (config) {
        ForceUse.FORCE_SYSTEM_ENFORCED -> getApplication<Application>().getString(R.string.label_enforced)
        ForceUse.FORCE_NONE -> getApplication<Application>().getString(R.string.label_none)
        null -> "?"
        else -> config.toString()
    }

    override fun onCleared() {
        cleared = true
        if (listenToShizuku) {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
}
