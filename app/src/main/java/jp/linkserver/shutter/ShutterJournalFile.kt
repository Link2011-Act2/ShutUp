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
        return ShutterJournalCodec.decode(text)
    }

    @Synchronized override fun save(journal: ShutterJournal) {
        val text = ShutterJournalCodec.encode(journal)
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
