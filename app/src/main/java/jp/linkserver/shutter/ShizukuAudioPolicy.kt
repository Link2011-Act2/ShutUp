package jp.linkserver.shutter

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import jp.linkserver.shutter.maintain.AudioPolicyConnection
import jp.linkserver.shutter.maintain.AudioPolicyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Connection bookkeeping is main-thread confined; IPC runs on the coordinator's IO thread. */
internal object ShizukuAudioPolicy {
    private const val TIMEOUT_MS = 8_000L
    private val main = Handler(Looper.getMainLooper())
    private var service: IShutterService? = null
    private var binding = false
    private var waitingForBinder = false
    private val waiting = mutableListOf<(IShutterService?) -> Unit>()
    private val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShutterService::class.java.name))
        .daemon(false).processNameSuffix("audio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            val bound = binder?.takeIf { it.pingBinder() }?.let { IShutterService.Stub.asInterface(it) }
            service = bound
            finish(bound)
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; binding = false }
    }
    private val received = Shizuku.OnBinderReceivedListener { connect() }
    private val timeout = Runnable { finish(null); detachPendingBinding() }

    fun status(): ShutterSound.Status = runCatching {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShutterSound.Status.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShutterSound.Status.NOT_AUTHORIZED
            else -> ShutterSound.Status.READY
        }
    }.getOrDefault(ShutterSound.Status.NOT_RUNNING)

    fun connector(context: Context) = AudioPolicyConnector {
        val bound = withContext(Dispatchers.Main.immediate) { awaitService() }
        bound?.let {
            object : AudioPolicyConnection {
                override fun read() = it.forSystem.also { value -> ShutterSound.sync(context, value) }
                override fun write(value: Int) {
                    it.forSystem = value
                    ShutterSound.sync(context, value, changed = true)
                }
            }
        }
    }

    private suspend fun awaitService(): IShutterService? {
        var callback: ((IShutterService?) -> Unit)? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val receiver: (IShutterService?) -> Unit = { if (continuation.isActive) continuation.resume(it) }
                callback = receiver
                waiting += receiver
                if (waiting.size == 1) { main.postDelayed(timeout, TIMEOUT_MS); connect() }
            }
        } finally {
            // This runs on Main before connect() returns/cancels, including for a stopped Worker.
            waiting.remove(callback)
            if (waiting.isEmpty()) { clearWait(); detachPendingBinding() }
        }
    }

    private fun connect() {
        if (waiting.isEmpty()) return
        when (status()) {
            ShutterSound.Status.NOT_RUNNING -> {
                service = null
                if (!Shizuku.pingBinder()) {
                    if (!waitingForBinder) {
                        waitingForBinder = true
                        Shizuku.addBinderReceivedListener(received, main)
                    }
                }
                else finish(null)
            }
            ShutterSound.Status.NOT_AUTHORIZED -> finish(null)
            ShutterSound.Status.READY -> {
                service?.takeIf { it.asBinder().pingBinder() }?.let { finish(it); return }
                if (!binding) try {
                    binding = true
                    Shizuku.bindUserService(args, connection)
                } catch (_: RuntimeException) { binding = false; finish(null) }
            }
        }
    }

    private fun clearWait() {
        main.removeCallbacks(timeout)
        Shizuku.removeBinderReceivedListener(received)
        waitingForBinder = false
    }
    private fun finish(bound: IShutterService?) {
        clearWait()
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(bound) }
    }
    private fun detachPendingBinding() {
        if (binding) { runCatching { Shizuku.unbindUserService(args, connection, false) }; binding = false }
    }
}
