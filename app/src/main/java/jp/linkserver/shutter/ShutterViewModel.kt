package jp.linkserver.shutter

import android.app.Application
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.shutter.maintain.AutoMaintainRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

data class ShutterUiState(
    val status: ShutterSound.Status = ShutterSound.Status.NOT_RUNNING,
    val current: Int? = null,
    val busy: Boolean = false,
    val error: String = "",
    val checking: Boolean = false,
    val defaultValue: Int? = ForceUse.deviceDefault(),
    val persistent: Boolean = false,
    val restoresSystemDefault: Boolean = false,
    val hasChange: Boolean = false,
    val restorable: Boolean? = null,
    val requestingPermission: Boolean = false,
    val nonMainUserWarning: Boolean = false,
    val nonMainUserConfirmed: Boolean = false,
    val releaseConfirmationPending: Boolean = false,
    val changingMethod: Boolean = false,
    val cscAbsent: Boolean = false,
    val releaseAllowed: Boolean = true,
    val emulatingCsc: Boolean = false
) {
    val working get() = busy || checking || requestingPermission || changingMethod
    val canRelease get() = releaseAllowed && !working && status == ShutterSound.Status.READY && current != null && current != ShutterTileService.released()
    val canRestore get() = !working && status == ShutterSound.Status.READY && current != null &&
        (restorable ?: (hasChange || defaultValue?.let { current != it } == true))
    val alreadyNone get() = !working && current == ForceUse.FORCE_NONE && defaultValue == ForceUse.FORCE_NONE

    internal fun withSnapshot(snapshot: ShutterSnapshot) = copy(
        defaultValue = snapshot.baseline, persistent = snapshot.persistent,
        hasChange = snapshot.hasChange, restorable = snapshot.restorable,
        restoresSystemDefault = snapshot.systemDefault != null || snapshot.cscAbsent, cscAbsent = snapshot.cscAbsent, releaseAllowed = snapshot.releaseAllowed,
        current = if (snapshot.persistent && snapshot.enforcement == Enforcement.UNKNOWN) null else current)
}

/** Keeps in-flight Shizuku operations alive through configuration and locale changes. */
class ShutterViewModel private constructor(
    application: Application,
    private val statusSource: () -> ShutterSound.Status,
    private val querySource: ((Result<Int>) -> Unit) -> Unit,
    private val listenToShizuku: Boolean,
    requestPermission: (Int) -> Unit
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ShutterSound::status,
        { ShutterSound.query(application, it) }, true, Shizuku::requestPermission)

    internal constructor(application: Application, statusSource: () -> ShutterSound.Status,
        querySource: ((Result<Int>) -> Unit) -> Unit) : this(application, statusSource, querySource, false,
        { error("Permission requests are unavailable in this test fixture") })

    private val maintenance = AutoMaintainRuntime.get(application)
    private val initialUserWarning = maintenance.userWarning
    private val initialGalaxySettings = maintenance.galaxySettings.value
    private val mutableState = MutableStateFlow(ShutterUiState(
        defaultValue = if (initialGalaxySettings.available && !initialGalaxySettings.forceAudioPolicy) null else ForceUse.deviceDefault(),
        persistent = initialGalaxySettings.available && !initialGalaxySettings.forceAudioPolicy,
        emulatingCsc = maintenance.warningDeveloperSettings.value.emulatesCsc && !initialGalaxySettings.forceAudioPolicy,
        nonMainUserWarning = initialUserWarning.affected, nonMainUserConfirmed = initialUserWarning.confirmed))
    val state = mutableState.asStateFlow()
    private var cleared = false
    private var queryGeneration = 0
    internal val autoMaintainState = maintenance.controller.state
    internal val autoMaintainOfferPending = maintenance.offer.pending
    internal val warningDeveloperSettings = maintenance.warningDeveloperSettings
    internal val galaxySettings = maintenance.galaxySettings
    private val permissionRequest = ShizukuPermissionRequest(viewModelScope, requestPermission, {
        mutableState.update { it.copy(requestingPermission = false, error = "") }
        refresh()
    }, onFailure = { failure, error ->
        Log.w("ShutterSound", "Shizuku permission request failed: $failure (uid=${Process.myUid()})", error)
    })
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener {
        ++queryGeneration
        mutableState.update { it.copy(status = ShutterSound.Status.NOT_RUNNING, current = null, checking = false) }
        permissionRequest.disconnected()
        refresh()
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        permissionRequest.onResult(code, result == PackageManager.PERMISSION_GRANTED)
    }

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(maintenance.warningDeveloperSettings, maintenance.galaxySettings) { settings, galaxy ->
                settings.emulatesCsc && !galaxy.forceAudioPolicy
            }.collect { emulated -> mutableState.update { it.copy(emulatingCsc = emulated) } }
        }
        viewModelScope.launch {
            maintenance.userWarningState.collect { warning ->
                mutableState.update { it.copy(nonMainUserWarning = warning.affected,
                    nonMainUserConfirmed = warning.confirmed,
                    releaseConfirmationPending = it.releaseConfirmationPending && warning === initialUserWarning) }
            }
        }
        viewModelScope.launch {
            permissionRequest.state.collect { request ->
                mutableState.update { it.copy(requestingPermission = request.requesting,
                    error = when {
                        request.failure != null -> application.getString(when (request.failure) {
                            ShizukuPermissionFailure.DENIED -> R.string.shizuku_permission_denied
                            ShizukuPermissionFailure.REQUEST_FAILED -> R.string.shizuku_permission_request_failed
                            ShizukuPermissionFailure.TIMED_OUT -> R.string.shizuku_permission_request_timeout
                        })
                        request.requesting -> ""
                        else -> it.error
                    }) }
            }
        }
        viewModelScope.launch {
            maintenance.controller.current.collect { current ->
                if (current != null && !state.value.working && state.value.status == ShutterSound.Status.READY)
                    mutableState.update { withSnapshot(it.copy(current = current, error = "")) }
            }
        }
        if (listenToShizuku) {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(permission)
        }
    }

    fun authorize() {
        if (cleared || state.value.working || permissionRequest.state.value.requesting ||
            state.value.status != ShutterSound.Status.NOT_AUTHORIZED) return
        Log.i("ShutterSound", "Requesting Shizuku permission (uid=${Process.myUid()})")
        mutableState.update { it.copy(requestingPermission = true, error = "") }
        permissionRequest.request()
    }

    fun refresh() {
        if (cleared || permissionRequest.state.value.requesting) return
        val status = statusSource()
        mutableState.update { it.copy(status = status) }
        if (status != ShutterSound.Status.READY) {
            ++queryGeneration
            mutableState.update { it.copy(current = null, checking = false,
                error = if (permissionRequest.state.value.failure != null) it.error else "") }
            return
        }
        permissionRequest.clearFailure()
        // Sticky Binder notifications, onResume and button taps share this check.
        // A duplicate must not invalidate the callback that will report its failure.
        if (state.value.working) return
        val generation = ++queryGeneration
        mutableState.update { it.copy(checking = true, error = "") }
        querySource query@{ result ->
            if (cleared || generation != queryGeneration || state.value.busy) return@query
            result.onSuccess { config ->
                mutableState.update { withSnapshot(it.copy(current = maintenance.controller.current.value ?: config,
                    checking = false, error = "")) }
            }
                .onFailure { error ->
                    Log.e("ShutterSound", "Could not read FOR_SYSTEM", error)
                    mutableState.update { withSnapshot(it.copy(current = null, checking = false,
                        error = getApplication<Application>().audioPolicyErrorMessage(error))) }
                }
        }
    }

    internal val releaseConfirmationRequired get() = maintenance.userWarning.confirmationRequired

    internal fun setGalaxyAudioPolicy(enabled: Boolean) {
        if (cleared || state.value.working) return
        val previous = galaxySettings.value
        mutableState.update { it.copy(changingMethod = true, releaseConfirmationPending = false) }
        viewModelScope.launch {
            try { maintenance.setGalaxyAudioPolicy(enabled) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w("ShutterSound", "Could not change Galaxy control method", error)
                Toast.makeText(getApplication(), if (error is ShutterMethodChangeBlocked)
                    R.string.galaxy_method_restore_first else R.string.galaxy_method_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                finishMethodChange(previous)
            }
        }
    }

    internal fun setForceSamsung(enabled: Boolean) = setWarningDeveloperSettings(
        warningDeveloperSettings.value.copy(forceSamsung = enabled))

    internal fun setForceNonMainUser(enabled: Boolean) = setWarningDeveloperSettings(
        warningDeveloperSettings.value.copy(forceNonMainUser = enabled))

    internal fun setEmulateCsc(enabled: Boolean) = setWarningDeveloperSettings(
        warningDeveloperSettings.value.copy(emulateCsc = enabled))

    private fun setWarningDeveloperSettings(settings: WarningDeveloperSettings) {
        if (cleared || state.value.working) return
        val previousEmulation = warningDeveloperSettings.value.emulatesCsc
        val previous = galaxySettings.value
        mutableState.update { it.copy(changingMethod = true, releaseConfirmationPending = false) }
        viewModelScope.launch {
            try { maintenance.setWarningDeveloperSettings(settings) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w("ShutterSound", "Could not change developer environment", error)
                Toast.makeText(getApplication(), if (error is ShutterMethodChangeBlocked)
                    R.string.galaxy_method_restore_first else R.string.developer_warning_save_failed, Toast.LENGTH_LONG).show()
            } finally { finishMethodChange(previous, previousEmulation != warningDeveloperSettings.value.emulatesCsc) }
        }
    }

    private fun finishMethodChange(previous: GalaxyControlSettings, changedEmulation: Boolean = false) {
        mutableState.update {
            if (changedEmulation || galaxySettings.value != previous) it.copy(changingMethod = false, current = null,
                defaultValue = null, persistent = galaxySettings.value.available && !galaxySettings.value.forceAudioPolicy,
                restoresSystemDefault = false, restorable = null, cscAbsent = false, releaseAllowed = true)
            else it.copy(changingMethod = false)
        }
        refresh()
    }

    fun release() = release(null)
    internal fun release(permit: ReleasePermit?) {
        if (cleared || state.value.working || state.value.status != ShutterSound.Status.READY) return
        if (permit == null && releaseConfirmationRequired) requestReleaseConfirmation()
        else apply(restore = false, permit = permit)
    }

    internal fun requestReleaseConfirmation() {
        if (!cleared && releaseConfirmationRequired)
            mutableState.update { it.copy(releaseConfirmationPending = true) }
    }

    internal fun cancelReleaseConfirmation() {
        mutableState.update { it.copy(releaseConfirmationPending = false) }
    }

    internal fun confirmRelease(remember: Boolean): ReleasePermit? {
        if (cleared || !state.value.releaseConfirmationPending || state.value.working) return null
        return try {
            maintenance.userWarning.confirm(remember).also {
                mutableState.update { it.copy(releaseConfirmationPending = false, nonMainUserConfirmed = true) }
            }
        } catch (error: Exception) {
            Log.w("ShutterSound", "Could not save Samsung user warning consent", error)
            mutableState.update { it.copy(releaseConfirmationPending = false,
                error = getApplication<Application>().getString(R.string.samsung_user_confirmation_save_failed)) }
            null
        }
    }

    fun restore() {
        cancelReleaseConfirmation()
        apply(restore = true)
    }

    internal fun setAutoMaintainEnabled(enabled: Boolean) {
        runCatching { maintenance.setEnabled(enabled) }.onFailure {
            Log.e("AutoMaintain", "Could not save settings", it)
            Toast.makeText(getApplication(), if (it is SamsungUserConfirmationRequired)
                R.string.samsung_user_auto_maintain_confirm_first else R.string.auto_maintain_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    internal fun respondToAutoMaintainOffer(maintain: Boolean) {
        runCatching { maintenance.respondToOffer(maintain) }.onFailure {
            Log.e("AutoMaintain", "Could not save first-release choice", it)
            Toast.makeText(getApplication(), if (it is SamsungUserConfirmationRequired)
                R.string.samsung_user_auto_maintain_confirm_first else R.string.auto_maintain_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun apply(restore: Boolean, permit: ReleasePermit? = null) {
        if (state.value.working || state.value.status != ShutterSound.Status.READY) return
        val config = if (restore) state.value.defaultValue else ForceUse.FORCE_NONE
        if (config == null && !state.value.hasChange) {
            mutableState.update { it.copy(error = getApplication<Application>().getString(R.string.device_default_unavailable)) }
            return
        }
        ++queryGeneration
        mutableState.update { it.copy(busy = true, error = "") }
        val callback: (Result<Unit>) -> Unit = callback@{ result ->
            if (cleared) return@callback
            mutableState.update { it.copy(busy = false) }
            val context = getApplication<Application>()
            result.onSuccess {
                Log.i("ShutterSound", "Shutter setting operation completed (restore=$restore)")
                val message = context.getString(when {
                    restore -> if (maintenance.snapshot.value?.restoredToSystemDefault == true)
                        R.string.result_default_restored else R.string.result_restored
                    state.value.nonMainUserWarning -> R.string.samsung_user_release_result
                    else -> R.string.result_released
                })
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                refresh()
            }.onFailure { error ->
                if (error is SamsungUserConfirmationRequired) {
                    mutableState.update { it.copy(releaseConfirmationPending = true, error = "") }
                    return@callback
                }
                Log.e("ShutterSound", "Could not change shutter setting (restore=$restore)", error)
                Toast.makeText(context, R.string.result_failed, Toast.LENGTH_LONG).show()
                mutableState.update { withSnapshot(it.copy(current = null, error = when (error) {
                    is ShutterMethodChangeBlocked -> context.getString(R.string.galaxy_method_restore_first)
                    is AudioPolicyException -> context.audioPolicyErrorMessage(error)
                    is ShutterVerificationException -> context.getString(R.string.shutter_change_unverified)
                    is SecurityException -> context.getString(R.string.audio_routing_permission_denied)
                    else -> context.getString(R.string.uninstall_reminder_error)
                })) }
            }
        }
        when {
            restore -> ShutterSound.restore(getApplication(), callback)
            permit != null -> ShutterSound.releaseConfirmed(getApplication(), permit, callback)
            else -> ShutterSound.release(getApplication(), callback)
        }
    }

    private fun withSnapshot(state: ShutterUiState): ShutterUiState {
        if (!listenToShizuku) return state // Preserve isolated refresh test fixtures.
        val snapshot = maintenance.snapshot.value ?: return state
        return state.withSnapshot(snapshot)
    }

    override fun onCleared() {
        cleared = true
        permissionRequest.close()
        if (listenToShizuku) {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
}
