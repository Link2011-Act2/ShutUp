package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class ShutterJournalCodecTest {
    @Test fun pendingWriteSurvivesSerializationWithItsOriginalAndBootCount() {
        for (original in listOf(null, 0, 1)) for (boot in listOf(null, 0, 123)) {
            val journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, original), boot)
            assertEquals(journal, ShutterJournalCodec.decode(ShutterJournalCodec.encode(journal)))
        }
        assertEquals(ShutterJournal(true), ShutterJournalCodec.decode(ShutterJournalCodec.encode(ShutterJournal(true))))
    }

    @Test fun legacyRecordsKeepTheirRestorationValueAndHaveNoPendingWrite() {
        for (version in 1..2) {
            assertEquals(ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1)),
                ShutterJournalCodec.decode("$version\nGALAXY_CSC\n1"))
            assertEquals(ShutterJournal(true, ShutterChange(ShutterMethod.AUDIO_POLICY, 11)),
                ShutterJournalCodec.decode("$version\nAUDIO_POLICY\n11"))
        }
        assertNull(ShutterJournalCodec.decode("2\nGALAXY_CSC\nabsent").change?.original)
    }

    @Test fun malformedPendingMarkersCannotSilentlyBecomeSettled() {
        for (text in listOf("3\nGALAXY_CSC\n1", "3\nGALAXY_CSC\n1\n-1",
            "3\nGALAXY_CSC\n1\nunknown", "3\nAUDIO_POLICY\n11\n1")) {
            assertTrue(text, runCatching { ShutterJournalCodec.decode(text) }.isFailure)
        }
        assertTrue(runCatching { ShutterJournalCodec.encode(ShutterJournal(true, pendingCscWriteBoot = 1)) }.isFailure)
    }
}
