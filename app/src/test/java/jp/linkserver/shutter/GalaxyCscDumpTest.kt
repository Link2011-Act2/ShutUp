package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class GalaxyCscDumpTest {
    private val key = GalaxyCscSetting.KEY
    private fun row(value: String = "0", default: String? = "1", system: String? = "true") =
        "_id:904 name:$key pkg:com.samsung.sec.android.application.csc value:$value" +
            (default?.let { " default:$it" } ?: "") + (system?.let { " defaultSystemSet:$it" } ?: "") +
            " isValuePreservedInRestore:true"
    private fun dump(row: String, user: Int = 0) = "SYSTEM SETTINGS (user $user)\n$row\n"

    @Test fun allFourCurrentDefaultCombinationsAreIndependent() {
        for (value in 0..1) for (default in 0..1) {
            val info = GalaxyCscDump.parse(dump(row(value.toString(), default.toString())), 0)!!
            assertTrue(info.exists)
            assertEquals(value, info.value)
            assertEquals(default, info.trustedDefault)
            assertEquals(info, GalaxyCscSetting.decode(info.encode()))
        }
    }
    @Test fun anAbsentKeyIsDistinctFromZeroAndFromAnUnavailableDump() {
        val absent = GalaxyCscDump.parse(dump("_id:1 name:other value:0"), 0)!!
        assertFalse(absent.exists)
        assertNull(absent.value)
        assertNull(absent.trustedDefault)
        assertEquals(absent, GalaxyCscSetting.decode(absent.encode()))
        assertTrue(GalaxyCscDump.parse(dump(row()), 0)!!.exists)
        assertNull(GalaxyCscDump.parse("", 0))
    }
    @Test fun aPresentNullValueIsNotAnAbsentKey() {
        val info = GalaxyCscDump.parse(dump(row("{null}", null)), 0)!!
        assertTrue(info.exists)
        assertNull(info.value)
        assertNull(info.trustedDefault)
    }
    @Test fun missingDefaultsAndNonSystemDefaultsAreNotTrusted() {
        for (entry in listOf(row(default = null), row(system = "false"), row(system = null))) {
            val info = GalaxyCscDump.parse(dump(entry), 0)!!
            assertEquals(0, info.value)
            assertNull(info.trustedDefault)
        }
    }
    @Test fun onlyTheRequestedUsersSystemNamespaceIsUsed() {
        val input = "GLOBAL SETTINGS (user 0)\n${row(default = "0")}\n" +
            "SECURE SETTINGS (user 10)\n${row(default = "0")}\n" +
            dump(row(default = "0"), 0) + dump(row(default = "1"), 10)
        assertEquals(0, GalaxyCscDump.parse(input, 0)!!.trustedDefault)
        assertEquals(1, GalaxyCscDump.parse(input, 10)!!.trustedDefault)
        assertNull(GalaxyCscDump.parse(input, 11))
    }
    @Test fun similarKeysAndUnknownNamespacesAreNotAdopted() {
        assertFalse(GalaxyCscDump.parse(dump(row().replace(key, "${key}_other")), 0)!!.exists)
        assertFalse(GalaxyCscDump.parse("SYSTEM SETTINGS (user 0)\nUNKNOWN SETTINGS (user 0)\n${row()}", 0)!!.exists)
        assertNull(GalaxyCscDump.parse("SYSTEM SETTINGS\n${row()}", 0))
    }
    @Test fun fieldOrderAndAdditionalAttributesDoNotChangeTheResult() {
        val input = "defaultSystemSet:true default:1 value:0 pkg:com.android.shell name:$key extra:true _id:4"
        assertEquals(GalaxyCscSetting(true, 0, 1, true), GalaxyCscDump.parse(dump(input), 0))
    }
    @Test fun malformedValuesDuplicateFieldsAndDuplicateRecordsFailClosed() {
        for (entry in listOf(row("2"), row(default = "11"), row(system = "TRUE"),
            row() + " value:1", row() + "\n" + row(), row().replace(" value:0", ""))) {
            assertNull(GalaxyCscDump.parse(dump(entry), 0))
        }
        assertNull(GalaxyCscDump.parse(dump(row()) + dump(row()), 0))
    }
    @Test fun errorsAndTruncationCannotBecomeTrustedMetadata() {
        for (error in listOf("Can't find service: settings", "Permission Denial: settings", "DUMP TIMEOUT", "RemoteException")) {
            assertNull(GalaxyCscDump.parse(dump(row()) + error, 0))
        }
        assertNull(GalaxyCscDump.parse(dump("name:$key value:"), 0))
    }
    @Test fun failedCommandsAndStderrAreUnavailableEvenWithAValidRow() {
        assertNull(GalaxyCscDump.fromCommand(ShutterCommandOutput(dump(row()), "", 1), 0))
        assertNull(GalaxyCscDump.fromCommand(ShutterCommandOutput(dump(row()), "error", 0), 0))
        assertEquals(1, GalaxyCscDump.fromCommand(ShutterCommandOutput(dump(row()), "", 0), 0)!!.trustedDefault)
    }
    @Test fun invalidIpcPayloadsAreUnavailable() {
        for (text in listOf("unavailable", "present:0:11:true", "present:0:1:TRUE", "present:0:1:true:extra"))
            assertNull(GalaxyCscSetting.decode(text))
    }
}
