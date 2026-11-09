package jp.linkserver.shutter

import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercise real framework queries without changing audio policy or installing an update. */
class AndroidCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun legacyShutterDefaultIsReadableAndMatchesThePublicApiWhenAvailable() {
        val forced = LegacyShutterSoundPolicy.mustPlayShutterSound()
        val expected = if (forced) ForceUse.FORCE_SYSTEM_ENFORCED else ForceUse.FORCE_NONE
        assertEquals(expected, ForceUse.deviceDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(MediaActionSound.mustPlayShutterSound(), forced)
        }
        assertTrue(ShutterService().getForSystem() in listOf(ForceUse.FORCE_NONE, ForceUse.FORCE_SYSTEM_ENFORCED))
    }

    @Test fun installedVersionAndUpdateSignatureQueriesWorkOnThisOs() {
        val info = context.packageManager.installedPackageInfo(context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES)
        assertFalse(installedAppVersionName(context).isBlank())
        assertEquals(info.versionName, installedAppVersionName(context))
        assertNotNull(info.signingInfo)
        val archive = context.packageManager.archivePackageInfo(context.applicationInfo.sourceDir,
            PackageManager.GET_SIGNING_CERTIFICATES)!!
        assertNotNull(archive.signingInfo)
        assertEquals(info.signingInfo!!.apkContentsSigners.toSet(), archive.signingInfo!!.apkContentsSigners.toSet())
        assertTrue(
            context.packageManager.hasSigningCertificate(context.packageName,
                info.signingInfo!!.apkContentsSigners.first().toByteArray(), PackageManager.CERT_INPUT_RAW_X509))
        val inspector = AndroidApkInspector(context)
        val installed = inspector.installed()
        val release = jp.linkserver.goldy.GitHubRelease(1, installed.versionName,
            url = "https://github.com/Link2011-Act2/ShutUp/releases/tag/${installed.versionName}")
        ApkVerifier.verify(installed, inspector.inspect(File(context.applicationInfo.sourceDir)), release)
        val error = runCatching {
            ApkVerifier.verify(installed, inspector.inspect(File(instrumentation.context.applicationInfo.sourceDir)), release)
        }.exceptionOrNull()
        assertTrue(error is ApkException)
        // The instrumentation APK has no versionName, so inspection rejects it before package matching.
        assertEquals(ApkFailure.InvalidApk, (error as ApkException).reason)
    }
}
