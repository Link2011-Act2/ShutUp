package jp.linkserver.shutter

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import jp.linkserver.shutter.maintain.AutoMaintainRuntime

class ShutterTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        if (ShutterSound.status() != ShutterSound.Status.READY) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            return
        }
        ShutterSound.query(this) { result ->
            val current = qsTile ?: return@query
            val snapshot = AutoMaintainRuntime.get(this).snapshot.value
            val deviceDefault = snapshot?.baseline ?: ForceUse.deviceDefault()
            current.state = result.fold(
                { when {
                    snapshot != null -> if (snapshot.changed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    deviceDefault == null -> Tile.STATE_UNAVAILABLE
                    it != deviceDefault -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                } },
                { Tile.STATE_UNAVAILABLE }
            )
            current.updateTile()
        }
    }

    override fun onClick() {
        if (ShutterSound.status() != ShutterSound.Status.READY) return
        ShutterSound.toggle(this) { result ->
            if (result.exceptionOrNull() is SamsungUserConfirmationRequired)
                unlockAndRun { openReleaseConfirmation() }
            onStartListening()
        }
    }

    // The PendingIntent overload is available only on API 34+. Older OS versions
    // still require Intent, regardless of this app's targetSdk.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openReleaseConfirmation() {
        val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_CONFIRM_RELEASE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** The value that lets the ringer mode silence the shutter. */
        fun released() = ForceUse.FORCE_NONE

        fun isChanged(config: Int) = ForceUse.deviceDefault()?.let { config != it } == true
    }
}
