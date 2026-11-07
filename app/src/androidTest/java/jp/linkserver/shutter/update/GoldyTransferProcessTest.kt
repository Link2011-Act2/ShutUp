package jp.linkserver.shutter.update

import android.app.Application
import android.content.Intent
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.UpdateTransferState
import jp.linkserver.shutter.ui.restorePreferences
import jp.linkserver.shutter.ui.ShutterTheme
import jp.linkserver.shutter.ui.UpdateOverviewScreen
import jp.linkserver.shutter.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.security.MessageDigest

/** Execute each stage in a separate force-stopped process. No real installer is started. */
class GoldyTransferProcessTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val journal get() = app.getSharedPreferences("goldy_transfer_process_test", 0)
    private val ledger get() = app.getSharedPreferences("goldy_transfer_state", 0)
    private val installer get() = ApkInstaller.create(app)
    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(90_000) { while (!condition()) delay(25) } }
    private fun model(onLaunch: (Intent) -> Unit): UpdateDownloadViewModel {
        lateinit var model: UpdateDownloadViewModel
        instrumentation.runOnMainSync { model = UpdateDownloadViewModel(app,
            ApkDownloader.create(app, ApkDownloadConfig(UPDATE_REPOSITORY, ApkSelectionPolicies.ShutUp, ApkVersionBindings.Published)), installer, onLaunch) }
        waitFor { !model.state.value.restoring }
        return model
    }

    @Test fun stage1PrepareDurableLeasesAndSimulateHandoffGap() = runBlocking<Unit> {
        assertTrue(android.os.Build.MODEL.contains("sdk"))
        assertFalse("Finish an earlier staged test first", journal.contains("unknown"))
        instrumentation.uiAutomation.executeShellCommand("appops set ${app.packageName} REQUEST_INSTALL_PACKAGES allow")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val original = JSONObject()
        ledger.all.forEach { (key, value) -> original.put(key, if (value is Set<*>) JSONArray(value.toList()) else value) }
        check(journal.edit().clear().putString("original", original.toString()).putInt("seed_pid", Process.myPid()).commit())
        val source = File(File(app.cacheDir, "goldy-apks").apply { mkdirs() }, "goldy-${java.util.UUID.randomUUID()}.apk")
        File(app.applicationInfo.sourceDir).copyTo(source, overwrite = true)
        try {
            val inspector = AndroidApkInspector(app)
            val installed = inspector.installed()
            val release = GitHubRelease(1001, "v${installed.versionName}", url = "https://github.com/Link2011-Act2/ShutUp/releases/tag/v${installed.versionName}")
            val metadata = ApkVerifier.verify(installed, inspector.inspect(source), release, ApkVersionBindings.Published)
            val hash = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; hash.update(b, 0, n) } }
            val sha = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            // Test fixture receipt: fully authenticated above; prepare still revalidates the original and its own copy.
            val receipt = VerifiedApk::class.java.getConstructor(File::class.java, ApkMetadata::class.java, String::class.java, GitHubAsset::class.java)
                .newInstance(source, metadata, sha, GitHubAsset(1002, "shutup_test.apk", "https://github.com/Link2011-Act2/ShutUp/releases/download/${release.tag}/shutup_test.apk", source.length(), "sha256:$sha"))
            val handed = (installer.prepare(receipt) as InstallPreparation.Ready).request
            val unknown = (installer.prepare(receipt) as InstallPreparation.Ready).request
            var launches = 0
            withContext(Dispatchers.Main) { assertEquals(InstallLaunchResult.HandedOff, installer.launch(handed) { launches++ }) }
            assertEquals(1, launches)
            check(journal.edit().putString("handed", handed.id).putString("unknown", unknown.id).commit())
            check(ledger.edit().clear().putString("selected_request", unknown.id)
                .putString("request_tag_${unknown.id}", release.tag)
                .putStringSet("claimed", setOf(handed.id, unknown.id)).putStringSet("handed_off", setOf(handed.id)).commit())
        } finally { source.delete() }
        println("Transfer prepared pid=${Process.myPid()}")
    }

    @Test fun stage2RestoreGapWithoutAutomaticInstallerThenExplicitlyReopen() {
        assertNotEquals(journal.getInt("seed_pid", 0), Process.myPid())
        var launches = 0
        val model = model { launches++ }
        assertEquals(0, launches)
        assertEquals(UpdateTransferState.Ready, model.state.value.transfer)
        assertNotNull(model.state.value.message)
        val request = model.state.value.requests.single { it.id == journal.getString("unknown", null) }
        app.contentResolver.openInputStream(request.uri)!!.use { assertTrue(it.read() >= 0) }
        compose.setContent { ShutterTheme { UpdateOverviewScreen(null, {}, model) } }
        compose.onNodeWithTag("retained-reopen-${request.id}").performScrollTo().performClick()
        compose.onNodeWithTag("retained-confirmation").assertIsDisplayed()
        assertEquals(0, launches)
        compose.onNodeWithTag("retained-confirm").performClick()
        waitFor { model.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches)
        assertNull(model.state.value.message)
        check(journal.edit().putInt("reopen_pid", Process.myPid()).commit())
    }

    @Test fun stage3RestoreHandedOffAndReleaseOnlyExplicitly() = runBlocking<Unit> {
        assertNotEquals(journal.getInt("reopen_pid", 0), Process.myPid())
        var launches = 0
        val model = model { launches++ }
        assertEquals(0, launches)
        assertEquals(UpdateTransferState.HandedOff, model.state.value.transfer)
        for (key in listOf("handed", "unknown")) {
            val request = model.state.value.requests.single { it.id == journal.getString(key, null) }
            app.contentResolver.openInputStream(request.uri)!!.use { assertTrue(it.read() >= 0) }
            instrumentation.runOnMainSync { model.release(request) }
            waitFor { model.state.value.requests.none { it.id == request.id } }
        }
        assertEquals(0, launches)
        val backup = JSONObject(journal.getString("original", "{}")!!)
        restorePreferences(ledger, backup.keys().asSequence().associateWith { key ->
            when (val value = backup.get(key)) {
                is JSONArray -> (0 until value.length()).map { value.getString(it) }.toSet()
                else -> value
            }
        })
        journal.edit().clear().commit()
    }
}
