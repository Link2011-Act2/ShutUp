package jp.linkserver.shutter

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
        ShutterSound.toggle(this) { onStartListening() }
    }

    companion object {
        /** The value that lets the ringer mode silence the shutter. */
        fun released() = ForceUse.FORCE_NONE

        fun isChanged(config: Int) = ForceUse.deviceDefault()?.let { config != it } == true
    }
}
