package jp.linkserver.shutter.maintain

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import android.content.Context
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoMaintainWorkTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun hourlyWorkIsUniqueUnconstrainedAndCancelledWhenDisabled() {
        val scheduler = AutoMaintainScheduler(context)
        val work = WorkManager.getInstance(context)
        fun entries() = work.getWorkInfosForUniqueWork(AutoMaintainScheduler.WORK_NAME).get(10, TimeUnit.SECONDS)
        try {
            scheduler.reconcile(false)
            entries()
            scheduler.reconcile(true)
            val first = entries().single { !it.state.isFinished }
            assertEquals(TimeUnit.HOURS.toMillis(1), first.periodicityInfo!!.repeatIntervalMillis)
            assertEquals(NetworkType.NOT_REQUIRED, first.constraints.requiredNetworkType)
            scheduler.reconcile(true)
            assertEquals(first.id, entries().single { !it.state.isFinished }.id)
            scheduler.reconcile(false)
            assertEquals(WorkInfo.State.CANCELLED, entries().first { it.id == first.id }.state)
            scheduler.reconcile(true)
            assertEquals(1, entries().count { !it.state.isFinished })
        } finally { scheduler.reconcile(false); entries() }
    }

    @Test fun defaultOffWorkerCompletesSafelyWithoutChangingSavedSettings() = runBlocking {
        val runtime = AutoMaintainRuntime.get(context)
        val worker = TestListenableWorkerBuilder<AutoMaintainWorker>(context).build()
        // The normal default-off Worker must not attempt any Shizuku operation.
        assertEquals(AutoMaintainState(), runtime.controller.state.value)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(AutoMaintainState(), runtime.controller.state.value)
    }

    @Test fun workManagerCanInstantiateAndExecuteTheProductionWorker() {
        assertEquals(AutoMaintainState(), AutoMaintainRuntime.get(context).controller.state.value)
        val work = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<AutoMaintainWorker>().build()
        try {
            work.enqueue(request).result.get(10, TimeUnit.SECONDS)
            val result = runBlocking {
                withTimeout(10_000) {
                    var info = work.getWorkInfoById(request.id).get(2, TimeUnit.SECONDS)!!
                    while (!info.state.isFinished) {
                        delay(50)
                        info = work.getWorkInfoById(request.id).get(2, TimeUnit.SECONDS)!!
                    }
                    info
                }
            }
            assertEquals(WorkInfo.State.SUCCEEDED, result.state)
        } finally { work.cancelWorkById(request.id).result.get(10, TimeUnit.SECONDS) }
    }

    private fun worker(controller: AutoMaintainController) = TestListenableWorkerBuilder<AutoMaintainWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                AutoMaintainWorker(appContext, params) { controller.maintain() }
        }).build()

    @Test fun unavailableAndPausedWorkersFinishWithoutWritesOrRetries() = runBlocking {
        val prefs = context.getSharedPreferences("auto_maintain_worker_test", 0)
        prefs.edit().clear().commit()
        try {
            val store = AutoMaintainPreferences(context, prefs)
            store.save(AutoMaintainState(true, true))
            var connects = 0
            val controller = AutoMaintainController(store, AudioPolicyConnector { connects++; null })
            assertEquals(ListenableWorker.Result.success(), worker(controller).doWork())
            assertEquals(1, connects)
            controller.requestManual(ManualAction.RESTORE)
            assertEquals(ListenableWorker.Result.success(), worker(controller).doWork())
            assertEquals(1, connects)
            assertEquals(AutoMaintainState(true, false), store.load())
            val restarted = AutoMaintainController(AutoMaintainPreferences(context, prefs), AudioPolicyConnector {
                fail("A paused Worker must not connect after restart"); null
            })
            assertEquals(ListenableWorker.Result.success(), worker(restarted).doWork())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun cancellingAWorkerCancelsAndCleansItsConnectionBeforeReturning() = runBlocking {
        val prefs = context.getSharedPreferences("auto_maintain_worker_cancel_test", 0)
        prefs.edit().clear().commit()
        try {
            val store = AutoMaintainPreferences(context, prefs)
            store.save(AutoMaintainState(true, true))
            val entered = CompletableDeferred<Unit>()
            var cleaned = false
            val controller = AutoMaintainController(store, AudioPolicyConnector {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cleaned = true }
            })
            val running = launch { worker(controller).doWork() }
            entered.await()
            running.cancelAndJoin()
            assertTrue(cleaned)
            assertEquals(AutoMaintainState(true, true), store.load())
        } finally { prefs.edit().clear().commit() }
    }
}
