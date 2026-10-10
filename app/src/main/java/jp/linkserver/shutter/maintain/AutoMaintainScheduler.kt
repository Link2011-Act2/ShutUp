package jp.linkserver.shutter.maintain

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal class AutoMaintainScheduler(context: Context) {
    private val work = WorkManager.getInstance(context)
    fun reconcile(enabled: Boolean) {
        if (enabled) work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoMaintainWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS).build())
        else work.cancelUniqueWork(WORK_NAME)
    }
    companion object { const val WORK_NAME = "maintain-shutter-release" }
}
