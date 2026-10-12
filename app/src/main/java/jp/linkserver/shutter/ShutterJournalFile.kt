package jp.linkserver.shutter

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** noBackupFilesDir excludes both cloud backup and device-to-device transfer. */
internal class ShutterJournalFile(context: Context, name: String = "shutter-change-v1") : ShutterChangeStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, name))

    @Synchronized override fun load(): ShutterJournal {
        val text = try { file.openRead().bufferedReader().use { it.readText() } }
        catch (missing: java.io.FileNotFoundException) {
            // AtomicFile.openRead recovers an interrupted write from its backup.
            if (file.baseFile.exists()) throw missing
            return ShutterJournal()
        }
        val parts = text.trim().split('\n')
        check(parts.firstOrNull() == "1") { "Invalid shutter journal" }
        if (parts.size == 1) return ShutterJournal(true)
        check(parts.size == 3) { "Invalid shutter journal" }
        val method = ShutterMethod.valueOf(parts[1])
        val original = parts[2].toInt()
        check(if (method == ShutterMethod.GALAXY_CSC) original == 1 else
            original == ForceUse.FORCE_NONE || original == ForceUse.FORCE_SYSTEM_ENFORCED) {
            "Invalid restoration value"
        }
        return ShutterJournal(true, ShutterChange(method, original))
    }

    @Synchronized override fun save(journal: ShutterJournal) {
        check(journal.initialized)
        val text = journal.change?.let { "1\n${it.method.name}\n${it.original}" } ?: "1"
        val output = file.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            file.finishWrite(output)
            // AtomicFile logs some rename failures instead of throwing. Verify
            // the committed record before allowing a privileged device write.
            check(load() == journal) { "Could not commit shutter restoration information" }
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
