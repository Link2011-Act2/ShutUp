package jp.linkserver.shutter

import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import kotlin.system.exitProcess
import java.util.concurrent.TimeUnit

/**
 * Runs in a Shizuku user service process, as shell (or root). Audio policy writes
 * require MODIFY_AUDIO_ROUTING, which shell lacks on some OS versions and devices.
 */
class ShutterService : IShutterService.Stub() {

    private val getForceUse: Method
    private val setForceUse: Method

    init {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/media/AudioSystem;")
        val audioSystem = Class.forName("android.media.AudioSystem")
        getForceUse = audioSystem.getMethod("getForceUse", Int::class.java)
        setForceUse = audioSystem.getMethod("setForceUse", Int::class.java, Int::class.java)
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun getForSystem(): Int = getForceUse.invoke(null, ForceUse.FOR_SYSTEM) as Int

    override fun setForSystem(config: Int) {
        require(config == ForceUse.FORCE_NONE || config == ForceUse.FORCE_SYSTEM_ENFORCED) {
            "Only FORCE_NONE and FORCE_SYSTEM_ENFORCED are accepted, got $config"
        }
        val status = setForceUse.invoke(null, ForceUse.FOR_SYSTEM, config) as Int
        check(status == 0) { "AudioSystem.setForceUse returned $status" }
    }

    override fun getGalaxyShutter(userId: Int): String = checkNotNull(galaxySettings(userId, "get"))

    override fun setGalaxyShutter(userId: Int, value: Int): Boolean {
        require(value == 0 || value == 1)
        return galaxySettings(userId, "put", value.toString()) != null
    }

    override fun deleteGalaxyShutter(userId: Int): Boolean = galaxySettings(userId, "delete") != null

    override fun getGalaxyShutterInfo(userId: Int): String {
        require(userId >= 0)
        return runCatching {
            val output = ShutterCommand().run(listOf("/system/bin/dumpsys", "settings"))
            GalaxyCscDump.fromCommand(output, userId)?.encode() ?: "unavailable"
        }.getOrDefault("unavailable")
    }

    private fun galaxySettings(userId: Int, operation: String, value: String? = null): String? {
        require(userId == 0)
        // No shell interpolation or arbitrary command/key IPC. The subprocess
        // inherits the UserService's shell/root UID rather than the app caller's.
        // Invoke the binary directly rather than the settings shell wrapper, so
        // timeout cleanup does not leave an untracked child command behind.
        val args = mutableListOf("/system/bin/cmd", "settings", "--user", userId.toString(),
            operation, "system", "csc_pref_camera_forced_shuttersound_key")
        value?.let { args.add(it) }
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                if (operation != "get") return null
                error("Settings read timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            check(process.exitValue() == 0) { "Settings command failed: $output" }
            // Some settings implementations print an error with exit code zero.
            if (operation == "get") check(output == "0" || output == "1" || output == "null" ||
                output.toIntOrNull() != null) { "Settings read failed: $output" }
            else check(output.isEmpty()) { "Settings write failed: $output" }
            return output
        } finally {
            process.destroy()
            if (process.isAlive) process.destroyForcibly().waitFor()
        }
    }
}
