package jp.linkserver.shutter

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

data class ShutterUiState(
    val status: ShutterSound.Status = ShutterSound.Status.NOT_RUNNING,
    val current: Int? = null,
    val busy: Boolean = false,
    val error: String = ""
) {
    val canRelease get() = !busy && status == ShutterSound.Status.READY && current != null && current != ShutterTileService.released()
    val canRestore get() = !busy && status == ShutterSound.Status.READY && current != null && current != ForceUse.deviceDefault()
    val alreadyNone get() = !busy && current == ForceUse.FORCE_NONE && ForceUse.deviceDefault() == ForceUse.FORCE_NONE
}

/** Keeps in-flight Shizuku operations alive through configuration and locale changes. */
class ShutterViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ShutterUiState())
    val state = mutableState.asStateFlow()
    private var cleared = false
    private var queryGeneration = 0
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener { refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
    }

    fun authorize() {
        if (state.value.status == ShutterSound.Status.NOT_AUTHORIZED) Shizuku.requestPermission(1)
    }

    fun refresh() {
        if (cleared) return
        val generation = ++queryGeneration
        val status = ShutterSound.status()
        mutableState.update { it.copy(status = status) }
        if (status != ShutterSound.Status.READY) {
            mutableState.update { it.copy(current = null, error = "") }
            return
        }
        ShutterSound.query(getApplication()) { result ->
            if (cleared || generation != queryGeneration || state.value.busy) return@query
            result.onSuccess { config -> mutableState.update { it.copy(current = config) } }
                .onFailure { error ->
                    Log.e("ShutterSound", "Could not read FOR_SYSTEM", error)
                    mutableState.update { it.copy(current = null, error = error.message ?: error.toString()) }
                }
        }
    }

    fun apply(config: Int) {
        if (state.value.busy || state.value.status != ShutterSound.Status.READY) return
        val before = state.value.current
        ++queryGeneration
        mutableState.update { it.copy(busy = true, error = "") }
        ShutterSound.set(getApplication(), config) { result ->
            if (cleared) return@set
            mutableState.update { it.copy(busy = false) }
            val context = getApplication<Application>()
            result.onSuccess {
                Log.i("ShutterSound", "FOR_SYSTEM $before -> $config")
                Toast.makeText(context, context.getString(R.string.result_changed, label(before), label(config)), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Log.e("ShutterSound", "Could not set FOR_SYSTEM to $config", error)
                Toast.makeText(context, R.string.result_failed, Toast.LENGTH_LONG).show()
                mutableState.update { it.copy(error = error.message ?: error.toString()) }
            }
            refresh()
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
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
    }
}
