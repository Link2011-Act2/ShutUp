package jp.linkserver.shutter.update

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.UpdateAction
import jp.linkserver.goldy.compose.UpdateTransferState
import jp.linkserver.shutter.R
import jp.linkserver.shutter.installedAppVersionName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Identifies the pinned catalog/asset snapshot for presentation; never authenticates an APK. */
internal fun transferIdentity(candidate: UpdateCandidate?): String? = candidate?.release?.let { release ->
    val asset = ApkSelectionPolicies.ShutUp.select(release)
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(release.id.toString(), release.tag, release.url, asset?.id?.toString(), asset?.downloadUrl,
        asset?.size?.toString(), asset?.digest).forEach { value ->
        val bytes = value?.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
        if (bytes != null) digest.update(bytes)
    }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal data class UpdateDownloadState(
    val tag: String = "",
    val transfer: UpdateTransferState = UpdateTransferState.Idle,
    val requests: List<InstallRequest> = emptyList(),
    val message: String? = null,
    val restoring: Boolean = true,
    val releaseIdentity: String? = null
) {
    val busy get() = transfer is UpdateTransferState.Downloading || transfer == UpdateTransferState.Verifying ||
        transfer == UpdateTransferState.PreparingInstall
}

/** Sole transfer/install path. Production always uses the public Gold downloader and installer. */
internal class UpdateDownloadViewModel @JvmOverloads constructor(
    application: Application,
    private val downloader: ApkDownloader = ApkDownloader.create(application, ApkDownloadConfig(
        UPDATE_REPOSITORY, ApkSelectionPolicies.ShutUp, ApkVersionBindings.Published,
        userAgent = "ShutUp/${installedAppVersionName(application)}")),
    private val installer: ApkInstaller = ApkInstaller.create(application),
    private val startActivity: (Intent) -> Unit = { application.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private val mutableSelection = MutableStateFlow<UpdateCandidate?>(null)
    val selection = mutableSelection.asStateFlow()
    private val ledger = application.getSharedPreferences("goldy_transfer_state", 0)
    private var operation: Job? = null
    private var receipt: VerifiedApk? = null
    private var receiptRelease: GitHubRelease? = null
    private var selectedRequest: InstallRequest? = null
    private var selectedRequestTag: String? = null
    private var selectedRequestIdentity: String? = null

    init {
        operation = viewModelScope.launch {
            try {
                downloader.cleanPartialFiles()
                refreshRequests()
                val id = ledger.getString("selected_request", null)
                selectedRequest = state.value.requests.firstOrNull { it.id == id }
                selectedRequest?.let { request ->
                    selectedRequestTag = ledger.getString("request_tag_${request.id}", null)
                    selectedRequestIdentity = ledger.getString("request_identity_${request.id}", null)
                    val handedOff = ledger.getStringSet("handed_off", emptySet()).orEmpty().contains(request.id)
                    mutableState.update { it.copy(tag = selectedRequestTag.orEmpty(), releaseIdentity = selectedRequestIdentity,
                        transfer = if (handedOff) UpdateTransferState.HandedOff else UpdateTransferState.Ready,
                        message = if (!handedOff && claimed(request.id)) text(R.string.goldy_update_handoff_unknown) else null) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { fail(text(R.string.goldy_update_restore_failed)) }
            finally { mutableState.update { it.copy(restoring = false) } }
        }
    }
    fun selectCandidate(candidate: UpdateCandidate?) {
        if (operation?.isActive != true) mutableSelection.value = candidate
    }
    fun action(candidate: UpdateCandidate?, action: UpdateAction) {
        if (action == UpdateAction.CancelDownload) { cancel(); return }
        if (state.value.restoring || operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                mutableState.update { it.copy(message = null) }
                when (action) {
                    UpdateAction.OpenInstallPermissionSettings -> permissionSettings()
                    UpdateAction.Install -> {
                        val ready = selectedRequest.takeIf { selectedRequestTag == candidate?.release?.tag &&
                            selectedRequestIdentity == transferIdentity(candidate) }
                        if (ready != null) launch(ready, automatic = false) else download(candidate)
                    }
                    UpdateAction.Download -> download(candidate)
                    UpdateAction.CancelDownload -> Unit
                }
            } catch (cancel: CancellationException) {
                mutableState.update { it.copy(transfer = UpdateTransferState.Cancelled) }
                discardReceipt()
                throw cancel
            } catch (error: Exception) {
                discardReceipt()
                fail(errorMessage(error))
            } finally {
                withContext(NonCancellable) {
                    try { refreshRequests() } catch (_: Exception) {
                        mutableState.update { it.copy(message = text(R.string.goldy_update_restore_failed)) }
                    }
                }
            }
        }
    }
    fun cancel() {
        if (state.value.transfer is UpdateTransferState.Downloading || state.value.transfer == UpdateTransferState.Verifying)
            operation?.cancel()
    }
    private suspend fun download(candidate: UpdateCandidate?) {
        val release = candidate?.release ?: return
        val identity = transferIdentity(candidate)
        if (identity != selectedRequestIdentity) {
            selectedRequest = null; selectedRequestTag = null; selectedRequestIdentity = null
        }
        mutableSelection.value = candidate
        mutableState.update { it.copy(tag = release.tag, releaseIdentity = identity) }
        when (installer.permissionStatus()) {
            InstallPermission.UserPermissionRequired -> { permissionSettings(); return }
            InstallPermission.MissingManifestPermission, InstallPermission.Unavailable -> {
                fail(text(R.string.update_install_failed)); return
            }
            InstallPermission.Allowed -> Unit
        }
        if (receiptRelease != release) discardReceipt()
        if (receipt == null) {
            mutableState.update { it.copy(transfer = UpdateTransferState.Downloading(ApkProgress(0, null, 1))) }
            receipt = downloader.download(release) { progress ->
                mutableState.update { it.copy(transfer = if (progress.total != null && progress.bytes >= progress.total!!)
                    UpdateTransferState.Verifying else UpdateTransferState.Downloading(progress)) }
            }
            receiptRelease = release
        }
        mutableState.update { it.copy(transfer = UpdateTransferState.PreparingInstall, message = null) }
        when (val prepared = installer.prepare(checkNotNull(receipt))) {
            is InstallPreparation.PermissionRequired -> {
                mutableState.update { it.copy(transfer = UpdateTransferState.PermissionRequired) }
            }
            is InstallPreparation.Failed -> {
                discardReceipt(); fail(installError(prepared.error))
            }
            is InstallPreparation.Ready -> {
                selectedRequest = prepared.request
                selectedRequestTag = release.tag
                selectedRequestIdentity = identity
                withContext(Dispatchers.IO) {
                    check(ledger.edit().putString("selected_request", prepared.request.id)
                        .putString("request_tag_${prepared.request.id}", release.tag)
                        .putString("request_identity_${prepared.request.id}", identity).commit())
                }
                discardReceipt() // Gold's durable lease, not this cache source, now owns the URI lifetime.
                refreshRequests()
                launch(prepared.request, automatic = true)
            }
        }
    }
    private fun claimed(id: String) = ledger.getStringSet("claimed", emptySet()).orEmpty().contains(id)
    private suspend fun launch(request: InstallRequest, automatic: Boolean) {
        if (automatic && claimed(request.id)) {
            mutableState.update { it.copy(transfer = UpdateTransferState.Ready,
                message = text(R.string.goldy_update_handoff_unknown)) }
            return
        }
        mutableState.update { it.copy(transfer = UpdateTransferState.PreparingInstall, message = null) }
        // Claim BEFORE the callback. A crash in the handoff gap never causes automatic relaunch.
        withContext(Dispatchers.IO) {
            check(ledger.edit().putStringSet("claimed", ledger.getStringSet("claimed", emptySet()).orEmpty().toSet() + request.id).commit())
        }
        when (val result = installer.launch(request, startActivity)) {
            InstallLaunchResult.HandedOff -> {
                withContext(Dispatchers.IO) {
                    check(ledger.edit().putStringSet("handed_off", ledger.getStringSet("handed_off", emptySet()).orEmpty().toSet() + request.id).commit())
                }
                mutableState.update { it.copy(transfer = UpdateTransferState.HandedOff) }
            }
            is InstallLaunchResult.PermissionRequired -> mutableState.update { it.copy(transfer = UpdateTransferState.PermissionRequired) }
            is InstallLaunchResult.Failed -> fail(installError(result.error))
        }
    }
    private fun permissionSettings() {
        mutableState.update { it.copy(transfer = UpdateTransferState.PermissionRequired) }
        try { startActivity(installer.unknownSourcesSettingsIntent()) }
        catch (_: Exception) { fail(text(R.string.settings_open_failed)) }
    }
    /** Explicit user retry only; restoring/onResume never invokes this. */
    fun reopen(request: InstallRequest) {
        if (state.value.restoring || operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                selectedRequest = request
                selectedRequestTag = ledger.getString("request_tag_${request.id}", null)
                selectedRequestIdentity = ledger.getString("request_identity_${request.id}", null)
                mutableState.update { it.copy(tag = selectedRequestTag.orEmpty(), releaseIdentity = selectedRequestIdentity) }
                withContext(Dispatchers.IO) { check(ledger.edit().putString("selected_request", request.id).commit()) }
                launch(request, automatic = false)
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { fail(errorMessage(error)) }
        }
    }
    /** UI requires confirmation that the installer/readers have finished before calling. */
    fun release(request: InstallRequest) {
        if (state.value.restoring || operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                mutableState.update { it.copy(message = null) }
                installer.release(request)
                withContext(Dispatchers.IO) {
                    val edit = ledger.edit().putStringSet("claimed", ledger.getStringSet("claimed", emptySet()).orEmpty().toSet() - request.id)
                        .putStringSet("handed_off", ledger.getStringSet("handed_off", emptySet()).orEmpty().toSet() - request.id)
                        .remove("request_tag_${request.id}").remove("request_identity_${request.id}")
                    if (ledger.getString("selected_request", null) == request.id) edit.remove("selected_request")
                    check(edit.commit())
                }
                if (selectedRequest?.id == request.id) {
                    selectedRequest = null
                    selectedRequestTag = null; selectedRequestIdentity = null
                    mutableState.update { it.copy(transfer = UpdateTransferState.Idle) }
                }
                refreshRequests()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { fail(text(R.string.goldy_update_release_failed)) }
        }
    }
    private suspend fun refreshRequests() {
        val requests = installer.pendingRequests()
        mutableState.update { it.copy(requests = requests) }
    }
    private suspend fun discardReceipt() {
        val old = receipt
        receipt = null; receiptRelease = null
        if (old != null) withContext(NonCancellable + Dispatchers.IO) {
            if (old.file.exists() && !old.file.delete()) mutableState.update { it.copy(message = text(R.string.goldy_update_cleanup_failed)) }
        }
    }
    private fun fail(message: String) { mutableState.update { it.copy(transfer = UpdateTransferState.Failed(message), message = message) } }
    private fun text(id: Int) = getApplication<Application>().getString(id)
    private fun errorMessage(error: Exception): String = when (error) {
        is ApkException -> when (error.reason) {
            ApkFailure.SignerMismatch -> text(R.string.update_signing_mismatch)
            ApkFailure.InvalidApk, ApkFailure.PackageMismatch, ApkFailure.VersionCodeTooLow, ApkFailure.VersionMismatch -> text(R.string.update_invalid_apk)
            else -> text(R.string.update_download_failed)
        }
        else -> text(R.string.update_download_failed)
    }
    private fun installError(error: InstallError) = when (error.apkFailure) {
        ApkFailure.SignerMismatch -> text(R.string.update_signing_mismatch)
        ApkFailure.PackageMismatch, ApkFailure.VersionCodeTooLow, ApkFailure.VersionMismatch -> text(R.string.update_invalid_apk)
        else -> text(R.string.update_install_failed)
    }
}
