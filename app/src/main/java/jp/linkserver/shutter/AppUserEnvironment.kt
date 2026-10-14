package jp.linkserver.shutter

import android.os.Build
import android.os.Process

/** Constructed in the app process, never from the shell/root UserService identity. */
internal data class AppUserEnvironment(val manufacturer: String, val userId: Int) {
    val samsung get() = manufacturer.equals("samsung", ignoreCase = true)
    val needsSamsungUserWarning get() = samsung && userId != 0

    companion object {
        fun fromAppUid(manufacturer: String, appUid: Int) = AppUserEnvironment(manufacturer, appUid / 100_000)
        fun current() = fromAppUid(Build.MANUFACTURER, Process.myUid())
    }
}
