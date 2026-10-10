package jp.linkserver.shutter

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import jp.linkserver.shutter.maintain.AutoMaintainRuntime
import jp.linkserver.shutter.maintain.ManualAction

/** All entry points use the same process-wide intent and operation coordinator. */
object ShutterSound {
    enum class Status { NOT_RUNNING, NOT_AUTHORIZED, READY }
    fun status(): Status = ShizukuAudioPolicy.status()
    fun query(context: Context, callback: (Result<Int>) -> Unit) = AutoMaintainRuntime.get(context).query(callback)
    fun release(context: Context, callback: (Result<Unit>) -> Unit) =
        AutoMaintainRuntime.get(context).manual(ManualAction.RELEASE) { callback(it.map {}) }
    fun restore(context: Context, callback: (Result<Unit>) -> Unit) =
        AutoMaintainRuntime.get(context).manual(ManualAction.RESTORE) { callback(it.map {}) }
    fun toggle(context: Context, callback: (Result<Int>) -> Unit) =
        AutoMaintainRuntime.get(context).manual(ManualAction.TOGGLE, callback)

    internal fun sync(context: Context, config: Int, changed: Boolean = false) {
        runCatching {
            if (config == ForceUse.deviceDefault()) Notifications.cancel(context)
            else Notifications.showChanged(context)
            if (changed) TileService.requestListeningState(context, ComponentName(context, ShutterTileService::class.java))
        }.onFailure { Log.w("ShutterSound", "Could not synchronize notification/tile", it) }
    }
}
