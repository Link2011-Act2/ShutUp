package jp.linkserver.shutter

import android.content.Context
import android.content.SharedPreferences

/** Reuse the existing preferences file while keeping consent separate from release/restoration intent. */
internal class SamsungUserWarningPreferences(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("auto_maintain", Context.MODE_PRIVATE)
) : SamsungUserConsentStore {
    override fun load() = SamsungUserConsent(
        prefs.getString("samsungUserWarningEnvironment", null),
        prefs.getBoolean("samsungUserWarningSkip", false),
        prefs.getBoolean("samsungUserWarningAutomaticAllowed", false)
    )

    override fun save(consent: SamsungUserConsent) {
        check(prefs.edit().putString("samsungUserWarningEnvironment", consent.environment)
            .putBoolean("samsungUserWarningSkip", consent.skipWarning)
            .putBoolean("samsungUserWarningAutomaticAllowed", consent.automaticAllowed).commit()) {
            "Could not save Samsung user warning consent"
        }
    }
}
