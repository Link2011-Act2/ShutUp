package jp.linkserver.shutter.update

import android.app.Application
import android.content.Intent
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.shutter.ui.restorePreferences
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Run in separate processes. An interrupted transfer restores metadata, never verified authority. */
class UpdateTransferProcessRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val ledger get() = app.getSharedPreferences("goldy_transfer_state", 0)
    private val journal get() = app.getSharedPreferences("transfer_background_test", 0)
    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(90_000) { while (!condition()) delay(25) } }

    @Test fun stage1StartRealAndroidTransferBeforeProcessDeath() = runBlocking<Unit> {
        assertTrue(android.os.Build.MODEL.contains("sdk", ignoreCase = true) || android.os.Build.FINGERPRINT.contains("generic"))
        assertFalse(journal.contains("original"))
        val backup = JSONObject()
        ledger.all.forEach { (key, value) -> backup.put(key, if (value is Set<*>) JSONArray(value.toList()) else value) }
        journal.edit().putString("original", backup.toString()).putInt("pid", Process.myPid()).commit()
        ledger.edit().clear().commit()
        lateinit var runtime: UpdateTransferRuntime
        instrumentation.runOnMainSync {
            runtime = UpdateTransferRuntime(app, selfApkDownloader(app, 100), ApkInstaller.create(app), AndroidUpdateTransferExecution(app)) { error("No background installer") }
            UpdateTransferRuntime::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, runtime)
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        runtime.awaitInitialization()
        val owner = Any()
        instrumentation.runOnMainSync { runtime.setForeground(owner, true) }
        delay(100)
        val candidate = selfApkCandidate(app)
        instrumentation.runOnMainSync { runtime.action(candidate, UpdateAction.Download) }
        waitFor { ((runtime.state.value.transfer as? UpdateTransferState.Downloading)?.progress?.bytes ?: 0) > 65536 }
        assertEquals("running", ledger.getString("transfer_phase", null))
        instrumentation.runOnMainSync { runtime.setForeground(owner, false) }
        scenario.close()
        // Deliberately leave the owned Android job active. The runner force-stops this process.
    }

    @Test fun stage2RestorePausedMetadataAndExplicitlyRestartFullValidation() = runBlocking<Unit> {
        assertNotEquals(journal.getInt("pid", 0), Process.myPid())
        var launches = 0
        lateinit var model: UpdateDownloadViewModel
        instrumentation.runOnMainSync { model = transferTestModel(app, selfApkDownloader(app), ApkInstaller.create(app)) { launches++ } }
        waitFor { !model.state.value.restoring }
        assertTrue(model.state.value.waiting)
        assertTrue(model.state.value.transfer is UpdateTransferState.Failed)
        assertEquals(UpdateAction.Download, (model.state.value.transfer as UpdateTransferState.Failed).retry)
        assertNull(ledger.getString("transfer_ticket", null))
        assertNotNull(model.selection.value)
        assertEquals(0, launches)
        assertTrue(File(app.cacheDir, "goldy-apks").listFiles().orEmpty().none { it.extension == "part" })
        instrumentation.runOnMainSync { model.action(model.selection.value, UpdateAction.Download) }
        waitFor { model.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches)
        val request = model.state.value.requests.single { it.id == ledger.getString("selected_request", null) }
        app.contentResolver.openInputStream(request.uri)!!.use { assertTrue(it.read() >= 0) }
        // Test-only cleanup after recorded callbacks; normal UI has no file-release action.
        ApkInstaller.create(app).release(request)
        assertTrue(ApkInstaller.create(app).pendingRequests().none { it.id == request.id })
        val backup = JSONObject(journal.getString("original", "{}")!!)
        restorePreferences(ledger, backup.keys().asSequence().associateWith { key -> when (val value = backup.get(key)) {
            is JSONArray -> (0 until value.length()).map { value.getString(it) }.toSet()
            else -> value
        } })
        journal.edit().clear().commit()
    }
}
