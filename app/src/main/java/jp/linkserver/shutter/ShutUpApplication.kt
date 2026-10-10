package jp.linkserver.shutter

import android.app.Application
import jp.linkserver.shutter.maintain.AutoMaintainRuntime

class ShutUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoMaintainRuntime.get(this).initialize()
    }
}
