package jp.linkserver.shutter.update

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.shutter.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal data class UpdateDownloadState(
    val tag: String = "", val busy: Boolean = false,
    val downloaded: Long = 0, val total: Long? = null,
    val file: File? = null, val pendingInstall: Boolean = false, val failureMessage: String? = null
)

internal class UpdateDownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun download(release: GitHubRelease) {
        if (job?.isActive == true) return
        mutableState.value = UpdateDownloadState(release.tag, busy = true)
        job = viewModelScope.launch {
            try {
                val file = downloadUpdateApk(getApplication(), release) { bytes, total ->
                    mutableState.value = UpdateDownloadState(release.tag, true, bytes, total)
                }
                mutableState.value = UpdateDownloadState(release.tag, file = file, pendingInstall = true)
            } catch (error: CancellationException) {
                mutableState.value = UpdateDownloadState(release.tag)
                throw error
            } catch (error: Exception) {
                Log.e("ShutUpUpdate", "APK download failed", error)
                val context = getApplication<Application>()
                val actionable = listOf(R.string.update_invalid_apk, R.string.update_signing_mismatch)
                    .map(context::getString).firstOrNull { it == error.message }
                mutableState.value = UpdateDownloadState(release.tag,
                    failureMessage = actionable ?: context.getString(R.string.update_download_failed))
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun consumeInstallRequest() { mutableState.value = state.value.copy(pendingInstall = false) }
}

internal suspend fun downloadUpdateApk(
    context: Context, release: GitHubRelease, onProgress: (Long, Long?) -> Unit
): File {
    require(release.apkUrl != null && isRepositoryReleaseUrl(release.apkUrl, download = true))
    val coroutineContext = currentCoroutineContext()
    return runInterruptible(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.apk.part")
        val destination = File(directory, "update.apk")
        destination.delete()
        val connection = (URI(release.apkUrl).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ShutUp-Updater/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            var lastProgress = 0L
            onProgress(0, total)
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        bytes += count
                        if (bytes - lastProgress >= 256 * 1024 || bytes == total) {
                            onProgress(bytes, total)
                            lastProgress = bytes
                        }
                    }
                }
            }
            coroutineContext.ensureActive()
            check(total == null || bytes == total) { "Incomplete download" }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            check(release.apkSha256 == null || release.apkSha256.equals(actualDigest, true)) { "APK digest mismatch" }
            verifyUpdateApk(context, partial)
            check(partial.renameTo(destination)) { "Could not finalize APK" }
            destination
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}

/** Reject a different app or signing key before handing an APK to Android. */
internal fun verifyUpdateApk(context: Context, file: File) {
    val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
    val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    check(archive?.packageName == context.packageName) { context.getString(R.string.update_invalid_apk) }
    val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    check(archive.longVersionCode >= installed.longVersionCode) { context.getString(R.string.update_invalid_apk) }
    val signing = archive.signingInfo
    val certificates = if (signing?.hasMultipleSigners() == true) signing.apkContentsSigners
        else signing?.signingCertificateHistory
    check(!certificates.isNullOrEmpty() && certificates.any {
        context.packageManager.hasSigningCertificate(context.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509)
    }) { context.getString(R.string.update_signing_mismatch) }
}
