package jp.linkserver.shutter.maintain

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class AutoMaintainWorker internal constructor(context: Context, parameters: WorkerParameters,
    private val check: suspend () -> MaintainResult
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters,
        { AutoMaintainRuntime.get(context).checkFromWorker() })
    override suspend fun doWork(): Result {
        try {
            withTimeoutOrNull(12_000) { check() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Log.w("AutoMaintain", "Periodic recovery deferred", error) }
        // Deferred to the next hourly check or connection event, without a rapid retry loop.
        return Result.success()
    }
}
