package jp.linkserver.shutter

/** null info means unavailable; exists=false means a confirmed absent key. */
internal data class GalaxyCscSetting(
    val exists: Boolean,
    val value: Int?,
    val defaultValue: Int? = null,
    val defaultSystemSet: Boolean = false
) {
    val trustedDefault get() = defaultValue?.takeIf { defaultSystemSet && it in 0..1 }

    // Only this small, validated result crosses IPC; never the full settings dump.
    fun encode() = if (!exists) "absent" else
        "present:${value ?: "null"}:${defaultValue ?: "null"}:$defaultSystemSet"

    companion object {
        const val KEY = "csc_pref_camera_forced_shuttersound_key"
        fun decode(text: String): GalaxyCscSetting? {
            if (text == "absent") return GalaxyCscSetting(false, null)
            val fields = text.split(':')
            if (fields.size != 4 || fields[0] != "present") return null
            if (fields[1] !in listOf("0", "1", "null") ||
                fields[2] !in listOf("0", "1", "null") || fields[3] !in listOf("true", "false")) return null
            return GalaxyCscSetting(true, fields[1].toIntOrNull(), fields[2].toIntOrNull(), fields[3] == "true")
        }
    }
}

internal object GalaxyCscDump {
    private val header = Regex("^\\s*([A-Z_]+) SETTINGS(?: \\(user ([0-9]+)\\))?\\s*$")
    private val field = Regex("(?:^|\\s)([A-Za-z_][A-Za-z0-9_]*):([^\\s]+)")

    fun fromCommand(output: ShutterCommandOutput, userId: Int): GalaxyCscSetting? =
        if (output.exitCode != 0 || output.stderr.isNotBlank()) null else parse(output.stdout, userId)

    /** Call only after a successful, complete command; absence needs a SYSTEM block. */
    fun parse(text: String, userId: Int): GalaxyCscSetting? {
        require(userId >= 0)
        var inSystem = false
        var sawSystem = false
        var result: GalaxyCscSetting? = null
        for (line in text.lineSequence()) {
            if (line.contains("Permission Denial") || line.contains("Can't find service:") ||
                line.contains("DUMP TIMEOUT") || line.contains("Exception")) return null
            val section = header.matchEntire(line)
            if (section != null) {
                inSystem = section.groupValues[1] == "SYSTEM" && section.groupValues[2].toIntOrNull() == userId
                if (inSystem) {
                    if (sawSystem) return null
                    sawSystem = true
                }
                continue
            }
            // An unfamiliar section boundary must not inherit the previous namespace.
            if (line.trim().contains("SETTINGS") && !line.contains("name:")) inSystem = false
            if (!inSystem) continue
            val fields = field.findAll(line).toList()
            if (fields.none { it.groupValues[1] == "name" && it.groupValues[2] == GalaxyCscSetting.KEY }) continue
            if (result != null || fields.groupBy { it.groupValues[1] }.any { it.value.size != 1 }) return null
            val values = fields.associate { it.groupValues[1] to it.groupValues[2] }
            val current = values["value"] ?: return null
            if (current !in listOf("0", "1", "null", "{null}")) return null
            val default = values["default"]
            if (default != null && default !in listOf("0", "1", "null", "{null}")) return null
            val system = values["defaultSystemSet"]
            if (system != null && system !in listOf("true", "false")) return null
            result = GalaxyCscSetting(true, current.toIntOrNull(), default?.toIntOrNull(), system == "true")
        }
        return if (sawSystem) result ?: GalaxyCscSetting(false, null) else null
    }
}
