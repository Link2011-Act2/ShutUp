package jp.linkserver.shutter.maintain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.ShizukuAudioPolicy
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

/** Application-owned listener, independent of any Activity/ViewModel or Worker lifetime. */
internal class AutoMaintainRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    val controller = AutoMaintainController(AutoMaintainPreferences(app), ShizukuAudioPolicy.connector(app))
    val offer = AutoMaintainOffer(AutoMaintainOfferPreferences(app))
    private val scheduler by lazy { AutoMaintainScheduler(app) }
    private var initialized = false
    private var listening = false
    private var automatic: Job? = null
    private val received = Shizuku.OnBinderReceivedListener { checkNow() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> checkNow() }

    fun initialize() {
        if (initialized) return
        initialized = true
        reconcile()
        checkNow()
    }
    fun setEnabled(enabled: Boolean) {
        try { controller.setEnabled(enabled) } finally { reconcile() }
        automatic?.cancel()
        if (enabled) checkNow()
        offer.finish()
    }
    fun respondToOffer(maintain: Boolean) {
        // A manual restoration wins even if saving the offer's dismissal failed.
        if (maintain && !controller.state.value.armed) {
            offer.finish()
            return
        }
        offer.respond(maintain) { setEnabled(true) }
    }
    private fun reconcile() {
        val enabled = controller.state.value.enabled
        scheduler.reconcile(enabled)
        if (enabled && !listening) {
            listening = true
            Shizuku.addBinderReceivedListenerSticky(received, main)
            Shizuku.addRequestPermissionResultListener(permission)
        } else if (!enabled && listening) {
            listening = false
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeRequestPermissionResultListener(permission)
            automatic?.cancel()
        }
    }
    fun checkNow() {
        if (!controller.state.value.active || automatic?.isActive == true) return
        automatic = scope.launch {
            try { withTimeout(12_000) { withContext(Dispatchers.IO) { controller.maintain() } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.w("AutoMaintain", "Automatic recovery deferred", error) }
        }
    }
    fun manual(action: ManualAction, callback: (Result<Int>) -> Unit) {
        val request = try { controller.requestManual(action) }
        catch (error: Exception) { callback(Result.failure(error)); return }
        updateOffer { offer.manualRequested(action) }
        automatic?.cancel()
        scope.launch {
            val result = policyResult { controller.performManual(request, ForceUse.deviceDefault()) }
            updateOffer { offer.manualFinished(action, result, ForceUse.deviceDefault(), controller.state.value) }
            callback(result)
        }
    }
    fun query(callback: (Result<Int>) -> Unit) {
        scope.launch { callback(policyResult { controller.query() }) }
    }
    private fun updateOffer(block: () -> Unit) {
        runCatching(block).onFailure { Log.w("AutoMaintain", "Could not save first-release offer", it) }
    }
    private suspend fun policyResult(block: suspend () -> Int): Result<Int> = try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }

    /** Worker awaits directly; it never launches a callback in the application scope. */
    suspend fun checkFromWorker(): MaintainResult = withContext(Dispatchers.IO) { controller.maintain() }
    companion object {
        @Volatile private var instance: AutoMaintainRuntime? = null
        fun get(context: Context): AutoMaintainRuntime = instance ?: synchronized(this) {
            instance ?: AutoMaintainRuntime(context).also { instance = it }
        }
    }
}
