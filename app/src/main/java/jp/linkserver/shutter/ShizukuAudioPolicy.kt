package jp.linkserver.shutter

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import jp.linkserver.shutter.maintain.AudioPolicyConnection
import jp.linkserver.shutter.maintain.AudioPolicyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Connection bookkeeping is main-thread confined; IPC runs on the coordinator's IO thread. */
internal object ShizukuAudioPolicy {
    internal const val AUDIO_ROUTING_PERMISSION = "android.permission.MODIFY_AUDIO_ROUTING"
    private const val TIMEOUT_MS = 8_000L
    private val main = Handler(Looper.getMainLooper())
    private var service: IShutterService? = null
    private var binding = false
    private var waitingForBinder = false
    private val waiting = mutableListOf<(Result<IShutterService>) -> Unit>()
    private val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShutterService::class.java.name))
        // Include the IPC revision so a running pre-CSC service is recreated
        // even when a locally installed APK retains the same app version code.
        .daemon(false).processNameSuffix("audio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE * 100 + 3)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            val bound = binder?.takeIf { it.pingBinder() }?.let { IShutterService.Stub.asInterface(it) }
            service = bound
            finish(bound?.let { Result.success(it) }
                ?: Result.failure(AudioPolicyException(AudioPolicyFailure.SERVICE_DISCONNECTED)))
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; binding = false }
    }
    private val received = Shizuku.OnBinderReceivedListener { connect() }
    private val timeout = Runnable {
        val failure = when (status()) {
            ShutterSound.Status.NOT_RUNNING -> AudioPolicyFailure.NOT_RUNNING
            ShutterSound.Status.NOT_AUTHORIZED -> AudioPolicyFailure.NOT_AUTHORIZED
            ShutterSound.Status.READY -> AudioPolicyFailure.SERVICE_START_TIMEOUT
        }
        Log.w("ShutterSound", "Audio UserService connection timed out (${failure.name})")
        detachPendingBinding()
        finish(Result.failure(AudioPolicyException(failure)))
    }

    fun status(): ShutterSound.Status = runCatching {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShutterSound.Status.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShutterSound.Status.NOT_AUTHORIZED
            else -> ShutterSound.Status.READY
        }
    }.getOrDefault(ShutterSound.Status.NOT_RUNNING)

    fun connector(context: Context) = AudioPolicyConnector {
        val bound = withContext(Dispatchers.Main.immediate) { awaitService() }
        bound.let {
            object : AudioPolicyConnection {
                override fun read(): Int {
                    val value = try { it.forSystem }
                    catch (error: Exception) {
                        Log.e("ShutterSound", "Could not read audio UserService FOR_SYSTEM", error)
                        main.post { if (service === it) service = null }
                        throw AudioPolicyException(
                            if (error is DeadObjectException) AudioPolicyFailure.SERVICE_DISCONNECTED
                            else AudioPolicyFailure.READ_FAILED, error)
                    }
                    return value
                }
                override fun write(value: Int) {
                    if (Shizuku.checkRemotePermission(AUDIO_ROUTING_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException(context.getString(R.string.audio_routing_permission_denied))
                    }
                    it.forSystem = value
                }
            }
        }
    }

    fun shutterConnector(context: Context, store: ShutterChangeStore,
        legacyMaintainArmed: Boolean,
        environment: AppUserEnvironment = AppUserEnvironment.current(),
        forceAudioPolicy: () -> Boolean = { false },
        publish: (ShutterSnapshot) -> Unit): AudioPolicyConnector =
        object : AudioPolicyConnector {
            override val storedRestoration = true
            override suspend fun connect(): AudioPolicyConnection {
                val bound = withContext(Dispatchers.Main.immediate) { awaitService() }
                val backend = ShutterBackend(object : ShutterDevice {
                    override fun readAudio() = bound.forSystem
                    override fun writeAudio(value: Int) {
                        if (Shizuku.checkRemotePermission(AUDIO_ROUTING_PERMISSION) != PackageManager.PERMISSION_GRANTED)
                            throw SecurityException(context.getString(R.string.audio_routing_permission_denied))
                        bound.forSystem = value
                    }
                    // Settings.System is per-user; never implicitly write user 0
                    // when ShutUp is installed in another Android user/profile.
                    private val userId = environment.userId
                    override fun readCsc(): String? = bound.getGalaxyShutter(userId).takeUnless { it == "null" }
                    override fun readCscInfo(): GalaxyCscSetting? = GalaxyCscSetting.decode(bound.getGalaxyShutterInfo(userId))
                    override fun writeCsc(value: Int?) {
                        try {
                            val written = if (value == null) bound.deleteGalaxyShutter(userId)
                                else bound.setGalaxyShutter(userId, value)
                            if (!written) throw ShutterWriteUncertainException()
                        }
                        catch (error: RemoteException) { throw ShutterWriteUncertainException(error) }
                    }
                    override fun canWriteCsc() =
                        Shizuku.checkRemotePermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
                }, store, environment.samsung,
                    ForceUse.deviceDefault(), legacyMaintainArmed, forceAudioPolicy())
                return BackendPolicyConnection(backend, store, publish)
            }
        }

    private suspend fun awaitService(): IShutterService {
        var callback: ((Result<IShutterService>) -> Unit)? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val receiver: (Result<IShutterService>) -> Unit = {
                    if (continuation.isActive) continuation.resumeWith(it)
                }
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
                else finish(Result.failure(AudioPolicyException(AudioPolicyFailure.NOT_RUNNING)))
            }
            ShutterSound.Status.NOT_AUTHORIZED ->
                finish(Result.failure(AudioPolicyException(AudioPolicyFailure.NOT_AUTHORIZED)))
            ShutterSound.Status.READY -> {
                service?.takeIf { it.asBinder().pingBinder() }?.let { finish(Result.success(it)); return }
                if (!binding) try {
                    binding = true
                    Shizuku.bindUserService(args, connection)
                } catch (error: RuntimeException) {
                    binding = false
                    Log.e("ShutterSound", "Could not bind audio UserService", error)
                    finish(Result.failure(AudioPolicyException(AudioPolicyFailure.SERVICE_START_FAILED, error)))
                }
            }
        }
    }

    private fun clearWait() {
        main.removeCallbacks(timeout)
        Shizuku.removeBinderReceivedListener(received)
        waitingForBinder = false
    }
    private fun finish(bound: Result<IShutterService>) {
        clearWait()
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(bound) }
    }
    private fun detachPendingBinding() {
        if (binding) { runCatching { Shizuku.unbindUserService(args, connection, false) }; binding = false }
    }
}
