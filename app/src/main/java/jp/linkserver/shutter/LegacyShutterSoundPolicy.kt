package jp.linkserver.shutter

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/** Read AudioService's regional policy, independently of the mutable FOR_SYSTEM setting. */
internal object LegacyShutterSoundPolicy {
    private data class Accessors(
        val getService: Method,
        val asInterface: Method,
        val isCameraSoundForced: Method
    )

    private val accessors by lazy {
        check(HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/os/ServiceManager;",
            "Landroid/media/IAudioService;",
            "Landroid/media/IAudioService\$Stub;"
        )) { "Could not access the device shutter sound policy" }
        Accessors(
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java),
            Class.forName("android.media.IAudioService\$Stub").getMethod("asInterface", IBinder::class.java),
            Class.forName("android.media.IAudioService").getMethod("isCameraSoundForced")
        )
    }

    fun mustPlayShutterSound(): Boolean {
        val methods = accessors
        val binder = methods.getService.invoke(null, "audio") as? IBinder
            ?: error("AudioService is unavailable")
        val service = methods.asInterface.invoke(null, binder)
            ?: error("AudioService is unavailable")
        return methods.isCameraSoundForced.invoke(service) as Boolean
    }
}
