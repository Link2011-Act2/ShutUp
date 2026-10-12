package jp.linkserver.shutter

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ShutterJournalFileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun newInstanceRetainsMethodAndOriginalInTheExcludedBackupDirectory() {
        val name = "journal-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, name)
        try {
            val state = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
            ShutterJournalFile(context, name).save(state)
            assertEquals(state, ShutterJournalFile(context, name).load())
            assertTrue(file.isFile)
            assertEquals(context.noBackupFilesDir.canonicalPath, file.parentFile?.canonicalPath)
            ShutterJournalFile(context, name).save(ShutterJournal(true))
            assertEquals(ShutterJournal(true), ShutterJournalFile(context, name).load())
        } finally { file.delete() }
    }

    @Test fun interruptedAtomicWriteRecoversThePreviousRestorationRecord() {
        val name = "journal-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, name)
        val backup = File(context.noBackupFilesDir, "$name.bak")
        try {
            val state = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
            ShutterJournalFile(context, name).save(state)
            assertTrue(file.renameTo(backup))
            file.writeText("unfinished write")
            assertEquals(state, ShutterJournalFile(context, name).load())
        } finally { file.delete(); backup.delete() }
    }

    @Test fun corruptRestorationInformationFailsClosedRatherThanSelectingANewMethod() {
        val name = "journal-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, name)
        try {
            file.writeText("1\nGALAXY_CSC\n0")
            assertTrue(runCatching { ShutterJournalFile(context, name).load() }.isFailure)
        } finally { file.delete() }
    }
}
