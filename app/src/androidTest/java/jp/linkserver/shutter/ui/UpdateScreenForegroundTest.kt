package jp.linkserver.shutter.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.shutter.ShutterUiState
import jp.linkserver.shutter.update.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** A resumed host Activity is insufficient: the update screen itself must still be selected. */
class UpdateScreenForegroundTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val ledger get() = app.getSharedPreferences("goldy_transfer_state", 0)
    private val installer get() = ApkInstaller.create(app)
    private lateinit var original: Map<String, *>
    private lateinit var originalIds: Set<String>
    private lateinit var runtime: UpdateTransferRuntime
    private lateinit var model: UpdateDownloadViewModel
    private lateinit var candidate: UpdateCandidate
    private val launches = AtomicInteger()
    private var open by mutableStateOf(false)
    private var visible by mutableStateOf(true)
    private val execution = object : UpdateTransferExecution {
        var ticket: String? = null
        override suspend fun enqueue(ticket: String, size: Long?): Boolean { this.ticket = ticket; return true }
        override suspend fun cancel() = Unit
    }

    @Before fun setup() = runBlocking {
        assertTrue(app.packageName.endsWith(".goldyverification"))
        original = ledger.all.toMap()
        originalIds = installer.pendingRequests().map { it.id }.toSet()
        check(ledger.edit().clear().commit())
        candidate = selfApkCandidate(app)
        instrumentation.runOnMainSync {
            runtime = UpdateTransferRuntime(app, selfApkDownloader(app), installer, execution) { launches.incrementAndGet(); Unit }
            model = UpdateDownloadViewModel(app, runtime)
        }
        runtime.awaitInitialization()
        compose.setContent {
            if (visible) MaterialTheme {
                ShutterNavigation(state = ShutterUiState(), onAuthorize = {}, onRelease = {}, onRestore = {}, onRefresh = {},
                    updateState = GoldyState(availableUpdate = candidate), downloads = model,
                    openTransferRequest = open, onTransferRequestConsumed = { open = false })
            }
        }
        compose.waitForIdle()
    }

    @After fun cleanup() = runBlocking {
        if (!::original.isInitialized) return@runBlocking
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        if (::runtime.isInitialized) runtime.cancel()
        installer.pendingRequests().filter { it.id !in originalIds }.forEach { installer.release(it) }
        restorePreferences(ledger, original)
    }

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(60_000) { while (!condition()) delay(20) }
    }
    private fun openUpdate() {
        compose.runOnIdle { open = true }
        compose.waitForIdle()
    }
    private fun startDownload() {
        compose.runOnIdle { model.action(candidate, UpdateAction.Download) }
        waitFor { execution.ticket != null }
    }
    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun complete() = runBlocking { runtime.runScheduledTransfer(checkNotNull(execution.ticket)) }

    @Test fun leavingUpdateForHomeBeforeCompletionRetainsReadyUntilExplicitInstall() {
        compose.runOnIdle { model.action(candidate, UpdateAction.Download) }
        assertNull(execution.ticket) // Home remains resumed, but cannot authorize an installer.
        openUpdate()
        startDownload()
        back()
        complete()
        assertEquals(UpdateTransferState.Ready, runtime.state.value.transfer)
        assertEquals(0, launches.get())
        openUpdate()
        assertEquals(0, launches.get())
        compose.runOnIdle { model.action(candidate, UpdateAction.Install) }
        waitFor { runtime.state.value.transfer == UpdateTransferState.HandedOff }
        assertEquals(1, launches.get())
    }

    @Test fun remainingOnUpdateLaunchesOnceAndNavigationReturnDoesNotReplay() {
        openUpdate()
        startDownload()
        complete()
        assertEquals(UpdateTransferState.HandedOff, runtime.state.value.transfer)
        assertEquals(1, launches.get())
        back()
        openUpdate()
        waitFor { runtime.state.value.transfer == UpdateTransferState.Ready }
        assertEquals(1, launches.get())
    }
}
