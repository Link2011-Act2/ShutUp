package jp.linkserver.shutter.maintain

import android.content.Context
import android.content.SharedPreferences

internal class AutoMaintainPreferences(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("auto_maintain", Context.MODE_PRIVATE)
) : AutoMaintainStore {
    override fun load() = AutoMaintainState(
        prefs.getBoolean("autoMaintainEnabled", false),
        prefs.getBoolean("autoMaintainArmed", false)
    )
    override fun save(state: AutoMaintainState) {
        check(prefs.edit().putBoolean("autoMaintainEnabled", state.enabled)
            .putBoolean("autoMaintainArmed", state.armed).commit()) { "Could not save auto-maintain settings" }
    }
}
