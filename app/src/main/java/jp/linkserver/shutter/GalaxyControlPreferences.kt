package jp.linkserver.shutter

import android.content.Context
import android.content.SharedPreferences

internal data class GalaxyControlSettings(val available: Boolean = false, val forceAudioPolicy: Boolean = false)
internal class ShutterMethodChangeBlocked : IllegalStateException("Restore the existing change before switching shutter methods")

internal class GalaxyControlPreferences(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("galaxy_control", Context.MODE_PRIVATE),
    private val samsung: () -> Boolean = { AppUserEnvironment.current().samsung }
) {
    fun load(): GalaxyControlSettings {
        val available = samsung()
        return GalaxyControlSettings(available, available && prefs.getBoolean("forceAudioPolicy", false))
    }
    fun save(forceAudioPolicy: Boolean) {
        if (!samsung()) return
        check(prefs.edit().putBoolean("forceAudioPolicy", forceAudioPolicy).commit()) { "Could not save Galaxy control method" }
    }
}
