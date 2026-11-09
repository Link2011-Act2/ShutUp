package jp.linkserver.shutter.update

import android.app.Application
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.UpdateAction
import jp.linkserver.goldy.compose.UpdateTransferState
import jp.linkserver.shutter.ui.restorePreferences
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/** Real public Gold download/crypto/lease/provider pipeline; only network bytes and Activity launch are recorded. */
class GoldyTransferIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val ledger get() = app.getSharedPreferences("goldy_transfer_state", 0)
    private lateinit var original: Map<String, *>
    private lateinit var installer: ApkInstaller
    private lateinit var originalIds: Set<String>
    private lateinit var directory: File
    private lateinit var fixtureDirectory: File
    private lateinit var originalFiles: Set<String>
    private val launches = CopyOnWriteArrayList<Intent>()
    private val models = mutableListOf<UpdateDownloadViewModel>()
    private val own get() = File(app.applicationInfo.sourceDir)
    private val version get() = requireNotNull(app.packageManager.getPackageInfo(app.packageName, 0).versionName)

    @Before fun setup() = runBlocking {
        assertTrue("Run this test only on a disposable emulator", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        original = ledger.all.toMap()
        ledger.edit().clear().commit()
        directory = File(app.cacheDir, "goldy-apks").apply { mkdirs() }
        originalFiles = directory.list().orEmpty().toSet()
        fixtureDirectory = File(app.cacheDir, "goldy-transfer-integration").apply { mkdirs() }
        installer = ApkInstaller.create(app)
        originalIds = installer.pendingRequests().map { it.id }.toSet()
    }
    @After fun cleanup() = runBlocking {
        models.forEach { instrumentation.runOnMainSync { it.cancel() } }
        installer.pendingRequests().filter { it.id !in originalIds }.forEach { installer.release(it) }
        restorePreferences(ledger, original)
        newFiles().forEach { assertTrue(it.delete()) }
        fixtureDirectory.listFiles()?.forEach { assertTrue(it.delete()) }
        assertTrue(fixtureDirectory.delete())
    }
    private fun newFiles() = directory.listFiles().orEmpty().filter { it.name !in originalFiles && it.name != ".transfer.lock" }
    private fun sha(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun candidate(file: File = own, tag: String = "v$version", digest: String? = "sha256:${sha(file)}", size: Long = file.length()) =
        UpdateCandidate(GitHubRelease(101, tag, url = "https://github.com/Link2011-Act2/ShutUp/releases/tag/$tag", assets = listOf(
            GitHubAsset(102, "shutup_test.apk", "https://github.com/Link2011-Act2/ShutUp/releases/download/$tag/shutup_test.apk", size, digest))))

    /** Reflection is test-only because transport is deliberately not a production API. Inspector is never mocked. */
    private fun downloader(file: File, slow: Boolean = false): ApkDownloader {
        val connectionType = Class.forName("jp.linkserver.goldy.AssetConnection")
        val transportType = Class.forName("jp.linkserver.goldy.AssetTransport")
        val transport = Proxy.newProxyInstance(transportType.classLoader, arrayOf(transportType)) { _, method, _ ->
            check(method.name == "open")
            val input = file.inputStream()
            val closed = java.util.concurrent.atomic.AtomicBoolean(false)
            val stream = object : InputStream() {
                override fun read(): Int = input.read()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (slow) Thread.sleep(15)
                    if (closed.get()) throw IOException("closed")
                    return input.read(buffer, offset, minOf(length, if (slow) 1024 else length))
                }
                override fun close() { closed.set(true); input.close() }
            }
            Proxy.newProxyInstance(connectionType.classLoader, arrayOf(connectionType)) { _, call, values -> when (call.name) {
                "getStatus" -> 200
                "header" -> if (values?.firstOrNull() == "Content-Length") file.length().toString() else null
                "input" -> stream
                "close" -> { stream.close(); Unit }
                else -> error(call.name)
            } }
        }
        return ApkDownloader::class.java.getConstructor(ApkDownloadConfig::class.java, File::class.java,
            ApkInspector::class.java, transportType).newInstance(ApkDownloadConfig(UPDATE_REPOSITORY,
            ApkSelectionPolicies.ShutUp, ApkVersionBindings.Published, maxAttempts = 1), directory, AndroidApkInspector(app), transport)
    }
    private fun model(downloader: ApkDownloader): UpdateDownloadViewModel {
        lateinit var model: UpdateDownloadViewModel
        instrumentation.runOnMainSync { model = transferTestModel(app, downloader, installer) { launches.add(Intent(it)); Unit } }
        models.add(model)
        waitFor { !model.state.value.restoring }
        return model
    }
    private fun waitFor(condition: () -> Boolean) = runBlocking {
        try { withTimeout(90_000) { while (!condition()) delay(25) } }
        catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Transfer timeout: ${models.map { it.state.value }}; launches=${launches.map { it.action }}; files=${directory.list()?.toList()}", timeout)
        }
    }
    private fun action(model: UpdateDownloadViewModel, candidate: UpdateCandidate, action: UpdateAction = UpdateAction.Download) {
        instrumentation.runOnMainSync { model.action(candidate, action) }
    }

    @Test fun handoffOccursOnceAndRestorationOffersNormalExplicitInstall() = runBlocking {
        val source = downloader(own)
        val model = model(source)
        val update = candidate()
        instrumentation.runOnMainSync { model.action(update, UpdateAction.Download); model.action(update, UpdateAction.Download) }
        waitFor { model.state.value.transfer == UpdateTransferState.Ready }
        assertTrue(launches.isEmpty())
        instrumentation.runOnMainSync { model.action(update, UpdateAction.Install); model.action(update, UpdateAction.Install) }
        waitFor { model.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches.size)
        val request = model.state.value.requests.single { it.id !in originalIds }
        assertTrue(launches.single().flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = requireNotNull(launches.single().data)
        assertEquals("${app.packageName}.goldy.fileprovider", uri.authority)
        app.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
        val restored = model(source)
        assertEquals(UpdateTransferState.Ready, restored.state.value.transfer)
        assertEquals(1, launches.size)
        assertTrue(restored.state.value.requests.any { it.id == request.id })
        assertTrue(newFiles().isEmpty()) // caller receipt removed; leased URI still readable
        action(restored, update, UpdateAction.Install)
        waitFor { restored.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(2, launches.size)
        installer.release(request) // Only test cleanup; callbacks did not open an installer.
        assertTrue(installer.pendingRequests().none { it.id == request.id })
        try { app.contentResolver.openInputStream(uri); fail("Released URI") } catch (_: java.io.FileNotFoundException) { }
    }

    @Test fun permissionDenialRequiresExplicitRetryAndSettingsReturnNeverLaunchesInstaller() {
        // Android may kill an app when this AppOp is revoked. The runner sets deny BEFORE instrumentation.
        assertEquals(InstallPermission.UserPermissionRequired, installer.permissionStatus())
        val model = model(downloader(own))
        val update = candidate()
        action(model, update)
        waitFor { model.state.value.transfer == UpdateTransferState.PermissionRequired }
        assertTrue(launches.isEmpty())
        instrumentation.runOnMainSync {
            model.action(update, UpdateAction.OpenInstallPermissionSettings)
            model.action(update, UpdateAction.OpenInstallPermissionSettings)
        }
        waitFor { launches.isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, launches.single().action)
        assertEquals(UpdateTransferState.PermissionRequired, model.state.value.transfer)
        assertTrue(newFiles().isEmpty())
    }

    @Test fun preparedOlderApkRemainsRetainedWhenNewReleaseIsSelected() {
        val model = model(downloader(own))
        val update = candidate()
        action(model, update)
        waitFor { model.state.value.transfer == UpdateTransferState.Ready }
        val request = model.state.value.requests.single { it.id !in originalIds }
        val newer = jp.linkserver.shutter.ui.testUpdate("v99.0.0-Beta2", withApk = true)
        instrumentation.runOnMainSync { model.selectCandidate(newer) }
        assertEquals(newer, model.selection.value)
        assertTrue(model.state.value.requests.any { it.id == request.id })
        app.contentResolver.openInputStream(request.uri)!!.use { assertTrue(it.read() >= 0) }
        assertTrue(launches.isEmpty())
    }

    @Test fun permissionGrantAfterProcessRestartRequiresExplicitRetry() {
        assertEquals(InstallPermission.Allowed, installer.permissionStatus())
        val model = model(downloader(own))
        val update = candidate()
        Thread.sleep(200)
        assertTrue(launches.isEmpty())
        action(model, update)
        waitFor { model.state.value.transfer == UpdateTransferState.Ready }
        action(model, update, UpdateAction.Install)
        waitFor { model.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches.size)
    }

    @Test fun cancellationCleansPartialBytesAndRetryStartsFresh() {
        val model = model(downloader(own, slow = true))
        val update = candidate()
        action(model, update)
        waitFor { (model.state.value.transfer as? UpdateTransferState.Downloading)?.progress?.bytes?.let { it > 0 } == true }
        instrumentation.runOnMainSync { model.cancel() }
        waitFor { model.state.value.transfer == UpdateTransferState.Cancelled && newFiles().isEmpty() }
        assertTrue(launches.isEmpty())
        val retry = model(downloader(own))
        action(retry, update)
        waitFor { retry.state.value.transfer == UpdateTransferState.Ready }
        assertTrue(launches.isEmpty())
        action(retry, update, UpdateAction.Install)
        waitFor { retry.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches.size)
    }

    @Test fun sizeDigestPackageAndVersionMismatchNeverReachInstaller() {
        val foreign = File(fixtureDirectory, "foreign-package.apk")
        instrumentation.context.assets.open("goldy/foreign-package.apk").use { input -> foreign.outputStream().use { input.copyTo(it) } }
        val cases = listOf(own to candidate(size = own.length() + 1), own to candidate(digest = "sha256:" + "0".repeat(64)),
            own to candidate(tag = "v99.99.99-Release"), foreign to candidate(foreign))
        cases.forEach { (file, update) ->
            val model = model(downloader(file))
            action(model, update)
            waitFor { model.state.value.transfer is UpdateTransferState.Failed }
            assertTrue(launches.isEmpty())
            assertTrue(newFiles().isEmpty())
        }
    }

    @Test fun validSamePackageApkWithForeignSignatureIsRejected() {
        val file = File(fixtureDirectory, "foreign-signer.apk")
        instrumentation.context.assets.open("goldy/foreign-signer.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        val inspector = AndroidApkInspector(app)
        val archive = inspector.inspect(file)
        assertNotNull(archive)
        assertEquals(app.packageName, archive!!.packageName)
        try {
            ApkVerifier.verify(inspector.installed(), archive, candidate(file).release, ApkVersionBindings.Published)
            fail("Foreign signature must be refused independently of other metadata")
        } catch (error: ApkException) { assertEquals(ApkFailure.SignerMismatch, error.reason) }
        val model = model(downloader(file))
        action(model, candidate(file))
        waitFor { model.state.value.transfer is UpdateTransferState.Failed }
        assertEquals(app.getString(jp.linkserver.shutter.R.string.update_signing_mismatch),
            (model.state.value.transfer as UpdateTransferState.Failed).message)
        assertTrue(launches.isEmpty())
    }

    @Test fun modifiedPreparedRecordIsRejectedOnNormalInstall() = runBlocking {
        val model = model(downloader(own))
        val update = candidate()
        action(model, update)
        waitFor { model.state.value.transfer == UpdateTransferState.Ready }
        action(model, update, UpdateAction.Install)
        waitFor { model.state.value.transfer == UpdateTransferState.HandedOff }
        val request = model.state.value.requests.single { it.id !in originalIds }
        val uri = requireNotNull(launches.single().data)
        instrumentation.runOnMainSync { model.onForegroundChanged(false); model.onForegroundChanged(true) }
        waitFor { !model.state.value.restoring && model.state.value.transfer == UpdateTransferState.Ready }
        // The fixture is already installed, so Goldy can safely back its URI with the OS APK.
        // Mutate only this test's record; never modify the OS-installed APK.
        val record = File(app.filesDir, "goldy-apks/records/${request.id}.json")
        record.writeText(org.json.JSONObject(record.readText()).put("sha256", "0".repeat(64)).toString())
        action(model, update, UpdateAction.Install)
        waitFor { model.state.value.transfer is UpdateTransferState.Failed }
        assertEquals(1, launches.size)
        assertEquals(UpdateAction.Download, (model.state.value.transfer as UpdateTransferState.Failed).retry)
        try { app.contentResolver.openInputStream(uri); fail("Mutated URI") } catch (_: java.io.FileNotFoundException) { }
    }
}
