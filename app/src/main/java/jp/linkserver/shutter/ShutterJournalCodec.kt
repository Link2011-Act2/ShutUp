package jp.linkserver.shutter

/** Keep the recovery marker durable across app/service restarts and APK updates. */
internal object ShutterJournalCodec {
    fun decode(text: String): ShutterJournal {
        val parts = text.trim().split('\n')
        val version = parts.firstOrNull()
        check(version in listOf("1", "2", "3")) { "Invalid shutter journal" }
        if (parts.size == 1) return ShutterJournal(true)
        check(parts.size == if (version == "3") 4 else 3) { "Invalid shutter journal" }
        val method = ShutterMethod.valueOf(parts[1])
        val original = if (version != "1" && parts[2] == "absent") null else parts[2].toInt()
        check(if (method == ShutterMethod.GALAXY_CSC)
            if (version == "1") original == 1 else original == null || original in 0..1
        else original == ForceUse.FORCE_NONE || original == ForceUse.FORCE_SYSTEM_ENFORCED) {
            "Invalid restoration value"
        }
        val pendingBoot = if (version != "3" || parts[3] == "settled") null else parts[3].toInt().also {
            check(it >= 0 && method == ShutterMethod.GALAXY_CSC) { "Invalid pending CSC write" }
        }
        return ShutterJournal(true, ShutterChange(method, original), pendingBoot)
    }

    fun encode(journal: ShutterJournal): String {
        check(journal.initialized)
        check(journal.pendingCscWriteBoot == null ||
            journal.change?.method == ShutterMethod.GALAXY_CSC && journal.pendingCscWriteBoot >= 0)
        return journal.change?.let {
            "3\n${it.method.name}\n${it.original ?: "absent"}\n${journal.pendingCscWriteBoot ?: "settled"}"
        } ?: "3"
    }
}
