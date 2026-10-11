package jp.linkserver.shutter

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import rikka.shizuku.Shizuku

/** Verify the actual denied shell path without changing the device audio policy. */
class ShizukuAudioPermissionTest {
    @Test fun missingAudioRoutingPermissionExplainsTheFailureBeforeWriting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ShizukuAudioPolicy.status() == ShutterSound.Status.READY)
        assumeTrue(Shizuku.checkRemotePermission(ShizukuAudioPolicy.AUDIO_ROUTING_PERMISSION) ==
            PackageManager.PERMISSION_DENIED)
        val connection = withTimeout(10_000) { ShizukuAudioPolicy.connector(context).connect() }!!
        val before = connection.read()
        assumeTrue(before == ForceUse.FORCE_NONE || before == ForceUse.FORCE_SYSTEM_ENFORCED)
        val target = if (before == ForceUse.FORCE_NONE) ForceUse.FORCE_SYSTEM_ENFORCED else ForceUse.FORCE_NONE
        val error = runCatching { connection.write(target) }.exceptionOrNull()
        assertTrue(error is SecurityException)
        assertEquals(context.getString(R.string.audio_routing_permission_denied), error?.message)
        assertEquals(before, connection.read())
    }
}
