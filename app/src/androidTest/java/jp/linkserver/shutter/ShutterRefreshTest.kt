package jp.linkserver.shutter

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Drive the real refresh state machine without starting Shizuku or changing audio policy. */
class ShutterRefreshTest {
    @Test fun repeatedRefreshesDoNotHideTheFailureAndASecondAttemptCanRecover() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync {
            val store = ViewModelStore()
            val callbacks = mutableListOf<(Result<Int>) -> Unit>()
            val model = ShutterViewModel(app, { ShutterSound.Status.READY }, { callbacks += it })
            store.put("shutter", model)
            try {
                model.refresh()
                repeat(20) { model.refresh() }
                assertTrue(model.state.value.checking)
                assertFalse(model.state.value.canRelease)
                assertEquals(1, callbacks.size)
                callbacks.removeAt(0)(Result.failure(AudioPolicyException(AudioPolicyFailure.SERVICE_START_FAILED,
                    IllegalStateException("Binding failure"))))
                assertFalse(model.state.value.checking)
                assertNull(model.state.value.current)
                assertEquals(app.getString(R.string.audio_service_start_failed), model.state.value.error)
                assertEquals(ShutterSound.Status.READY, model.state.value.status)
                model.refresh()
                assertTrue(model.state.value.checking)
                assertEquals(1, callbacks.size)
                callbacks.removeAt(0)(Result.success(ForceUse.FORCE_SYSTEM_ENFORCED))
                assertFalse(model.state.value.checking)
                assertEquals(ForceUse.FORCE_SYSTEM_ENFORCED, model.state.value.current)
                assertEquals("", model.state.value.error)
            } finally { store.clear() }
        }
    }

    @Test fun aDisconnectInvalidatesTheOldCheckWithoutDroppingTheNewFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync {
            val store = ViewModelStore()
            var status = ShutterSound.Status.READY
            val callbacks = mutableListOf<(Result<Int>) -> Unit>()
            val model = ShutterViewModel(app, { status }, { callbacks += it })
            store.put("shutter", model)
            try {
                model.refresh()
                status = ShutterSound.Status.NOT_RUNNING
                model.refresh()
                assertFalse(model.state.value.checking)
                status = ShutterSound.Status.READY
                model.refresh()
                assertEquals(2, callbacks.size)
                callbacks[0](Result.success(ForceUse.FORCE_NONE))
                assertTrue(model.state.value.checking)
                assertNull(model.state.value.current)
                callbacks[1](Result.failure(AudioPolicyException(AudioPolicyFailure.QUERY_TIMEOUT)))
                assertFalse(model.state.value.checking)
                assertEquals(app.getString(R.string.audio_status_check_timeout), model.state.value.error)
            } finally { store.clear() }
        }
    }
}
