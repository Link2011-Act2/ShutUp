package jp.linkserver.shutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The notification's restore button. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        ShutterSound.restore(app) { result ->
            if (result.isFailure) Notifications.showRestoreFailed(app)
            pending.finish()
        }
    }
}
