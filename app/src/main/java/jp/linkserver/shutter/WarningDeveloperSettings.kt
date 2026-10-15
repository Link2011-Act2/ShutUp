package jp.linkserver.shutter

import android.content.Context
import android.content.SharedPreferences
import jp.linkserver.shutter.update.isIntDevUpdateBuild

/** One recognized environment for app behavior, separate from the actual SettingsProvider user. */
internal data class WarningDeveloperSettings(
    val available: Boolean = false,
    val forceSamsung: Boolean = false,
    val forceNonMainUser: Boolean = false
) {
    val simulated get() = available && (forceSamsung || forceNonMainUser)

    fun environment(actual: AppUserEnvironment) = AppEnvironment(actual, if (available) actual.copy(
        manufacturer = if (forceSamsung) "samsung" else actual.manufacturer,
        userId = if (forceNonMainUser && actual.userId == 0) 1 else actual.userId
    ) else actual)
}

internal class WarningDeveloperPreferences(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("warning_developer_settings", Context.MODE_PRIVATE),
    private val installedVersion: String = installedAppVersionName(context)
) {
    val simulationConsent = SamsungUserWarningPreferences(context, prefs)
    fun load() = if (isIntDevUpdateBuild(installedVersion)) WarningDeveloperSettings(
        available = true,
        forceSamsung = prefs.getBoolean("forceSamsung", false),
        forceNonMainUser = prefs.getBoolean("forceNonMainUser", false)
    ) else WarningDeveloperSettings()

    fun save(settings: WarningDeveloperSettings) {
        if (!isIntDevUpdateBuild(installedVersion)) return
        // Reset only the simulated consent so developers can repeat the first-release flow.
        simulationConsent.save(SamsungUserConsent())
        check(prefs.edit().putBoolean("forceSamsung", settings.forceSamsung)
            .putBoolean("forceNonMainUser", settings.forceNonMainUser).commit()) {
            "Could not save warning developer settings"
        }
    }
}
