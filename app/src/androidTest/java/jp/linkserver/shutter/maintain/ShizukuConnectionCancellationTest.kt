package jp.linkserver.shutter.maintain

import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.ShizukuAudioPolicy
import jp.linkserver.shutter.ShutterSound
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShizukuConnectionCancellationTest {
    @Test fun aCancelledConnectionRemovesItsCallbackAndTemporaryBinderListener() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ShutterSound.status() == ShutterSound.Status.NOT_RUNNING)
        fun field(name: String) = ShizukuAudioPolicy::class.java.getDeclaredField(name).apply { isAccessible = true }
        val callbacks = field("waiting")
        val listening = field("waitingForBinder")
        val pending = async(Dispatchers.IO) { ShizukuAudioPolicy.connector(context).connect() }
        withTimeout(5_000) {
            while (!withContext(Dispatchers.Main) { listening.getBoolean(ShizukuAudioPolicy) }) delay(10)
        }
        pending.cancelAndJoin()
        withContext(Dispatchers.Main) {
            assertTrue((callbacks.get(ShizukuAudioPolicy) as List<*>).isEmpty())
            assertFalse(listening.getBoolean(ShizukuAudioPolicy))
        }
    }
}
