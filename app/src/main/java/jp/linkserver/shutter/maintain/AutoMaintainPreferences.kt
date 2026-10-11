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

internal class AutoMaintainOfferPreferences(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("auto_maintain", Context.MODE_PRIVATE)
) : AutoMaintainOfferStore {
    override fun load(): AutoMaintainOfferStage {
        val saved = prefs.getString("autoMaintainReleaseOffer", AutoMaintainOfferStage.READY.name)
        return AutoMaintainOfferStage.entries.firstOrNull { it.name == saved } ?: AutoMaintainOfferStage.HANDLED
    }
    override fun save(stage: AutoMaintainOfferStage) {
        check(prefs.edit().putString("autoMaintainReleaseOffer", stage.name).commit()) {
            "Could not save auto-maintain offer"
        }
    }
}
