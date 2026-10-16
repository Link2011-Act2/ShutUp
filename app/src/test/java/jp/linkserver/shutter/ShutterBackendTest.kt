package jp.linkserver.shutter

import jp.linkserver.shutter.maintain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShutterBackendTest {
    private class Store(var journal: ShutterJournal = ShutterJournal()) : ShutterChangeStore {
        var fail = false
        override fun load() = journal
        override fun save(journal: ShutterJournal) { check(!fail); this.journal = journal }
    }
    private class Device(var audio: Int = 11, var csc: String? = "1") : ShutterDevice {
        val writes = mutableListOf<String>()
        var permission = true
        var audioReadable = true
        var cscReadable = true
        var cscInfo: GalaxyCscSetting? = null
        var cscInfoReadable = true
        var cscInfoReads = 0
        var cscWrite: ((Int?) -> Unit)? = null
        var audioWrite: ((Int) -> Unit)? = null
        var beforeCscRead: (() -> Unit)? = null
        override fun readAudio(): Int { check(audioReadable); return audio }
        override fun writeAudio(value: Int) {
            writes += "audio:$value"
            audioWrite?.invoke(value) ?: run { audio = value }
        }
        override fun readCsc(): String? { beforeCscRead?.invoke(); check(cscReadable); return csc }
        override fun readCscInfo(): GalaxyCscSetting? { cscInfoReads++; check(cscInfoReadable); return cscInfo ?: if (csc == null) GalaxyCscSetting(false, null) else null }
        override fun writeCsc(value: Int?) {
            writes += "csc:$value"
            cscWrite?.invoke(value) ?: run { csc = value?.toString() }
        }
        override fun canWriteCsc() = permission
    }
    private class Fixture(samsung: Boolean = true, default: Int? = 11, legacy: Boolean = false, var audioMode: Boolean = false) {
        val store = Store()
        val device = Device()
        private val samsung = samsung
        private val default = default
        private val legacy = legacy
        var bootCount: Int? = 1
        fun backend() = ShutterBackend(device, store, samsung, default, legacy, forceAudioPolicy = audioMode, bootCount = bootCount)
        val published = mutableListOf<ShutterSnapshot>()
        fun connector() = object : AudioPolicyConnector {
            override val storedRestoration = true
            override suspend fun connect() = BackendPolicyConnection(backend(), store) { published += it }
        }
        fun controller(initial: AutoMaintainState = AutoMaintainState()) = AutoMaintainController(
            object : AutoMaintainStore {
                var saved = initial
                override fun load() = saved
                override fun save(state: AutoMaintainState) { saved = state }
            }, connector())
    }

    @Test fun knownSystemDefaultsAreAdoptedForAllCurrentValuesWithoutWriting() {
        for (current in 0..1) for (default in 0..1) {
            val f = Fixture()
            f.device.audio = 0
            f.device.csc = current.toString()
            f.device.cscInfo = GalaxyCscSetting(true, current, default, true)
            val state = f.backend().inspect()
            assertEquals(ShutterMethod.GALAXY_CSC, state.method)
            assertEquals(current, state.rawValue)
            assertEquals(default, state.restoreValue)
            assertEquals(default, state.systemDefault)
            assertEquals(current != default, state.restorable)
            assertTrue(f.device.writes.isEmpty())
            assertNull(f.store.journal.change)
        }
    }
    @Test fun anotherToolsZeroCanBeRestoredAfterReinstallWithoutAnOldJournal() {
        for (initialized in listOf(false, true)) {
            val f = Fixture()
            f.store.journal = ShutterJournal(initialized)
            f.device.audio = 0
            f.device.csc = "0"
            f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
            f.device.cscWrite = {
                assertEquals(0, f.store.journal.change!!.original)
                f.device.csc = it?.toString()
            }
            val restored = f.backend().restore()
            assertEquals("1", f.device.csc)
            assertEquals(listOf("csc:1"), f.device.writes)
            assertTrue(restored.restoredToSystemDefault == true)
            assertNull(f.store.journal.change)
        }
    }
    @Test fun currentOneCanBeRestoredToSystemDefaultZero() {
        val f = Fixture()
        f.device.cscInfo = GalaxyCscSetting(true, 1, 0, true)
        f.backend().restore()
        assertEquals("0", f.device.csc)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertNull(f.store.journal.change)
    }
    @Test fun systemDefaultOverridesEverySavedCurrentValue() {
        for (original in listOf(null, 0, 1)) for (default in 0..1) {
            val f = Fixture()
            f.device.audio = 0
            f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, original))
            f.device.csc = (1 - default).toString()
            f.device.cscInfo = GalaxyCscSetting(true, 1 - default, default, true)
            f.backend().restore()
            assertEquals(default.toString(), f.device.csc)
            assertEquals(listOf("csc:$default"), f.device.writes)
            assertNull(f.store.journal.change)
        }
    }
    @Test fun defaultsAreReadAgainRatherThanPersistentlyReplacingTheOriginal() {
        val f = Fixture()
        f.device.cscInfo = GalaxyCscSetting(true, 1, 1, true)
        f.backend().release()
        assertEquals(1, f.store.journal.change!!.original)
        f.device.cscInfo = GalaxyCscSetting(true, 0, 0, true)
        val after = f.backend().restore()
        assertEquals("0", f.device.csc)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertTrue(after.restoredToSystemDefault == true)
        assertNull(f.store.journal.change)
    }
    @Test fun missingUntrustedOrFailedMetadataDoesNotDisableExistingCscOperations() {
        for (kind in 0..2) {
            val f = Fixture()
            when (kind) {
                0 -> f.device.cscInfo = null
                1 -> f.device.cscInfo = GalaxyCscSetting(true, 1, 0, false)
                2 -> f.device.cscInfoReadable = false
            }
            f.backend().release()
            val restored = f.backend().restore()
            assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
            assertEquals(false, restored.restoredToSystemDefault)
        }
    }
    @Test fun zeroWithUnknownDefaultAndNoJournalIsNeverGuessedAsDefaultOne() {
        val f = Fixture()
        f.device.audio = 0
        f.device.csc = "0"
        f.device.cscInfo = GalaxyCscSetting(true, 0)
        f.backend().restore()
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
    }
    @Test fun metadataCannotInventAKeyAndUnwritableCscNeverSelectsAudio() {
        for (absent in listOf(true, false)) {
            val f = Fixture()
            f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
            if (absent) f.device.csc = null else f.device.permission = false
            assertEquals(ShutterMethod.GALAXY_CSC, f.backend().inspect().method)
            assertTrue(runCatching { f.backend().release() }.isFailure)
            assertTrue(f.device.writes.isEmpty())
        }
    }
    @Test fun releaseNeverChangesCscZeroOrIndependentAudioEnforcement() {
        val f = Fixture()
        f.device.csc = "0"
        f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
        assertEquals(Enforcement.RELEASED, f.backend().release().enforcement)
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
    }
    @Test fun restoringASavedAbsentStateDeletesTheKeyOnlyWhenDefaultIsUnknown() {
        val f = Fixture()
        f.device.csc = "0"
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, null))
        f.backend().restore()
        assertNull(f.device.csc)
        assertEquals(listOf("csc:null"), f.device.writes)
        assertNull(f.store.journal.change)
    }
    @Test fun anInvalidReadbackCannotConfirmRestorationToAnAbsentState() {
        val f = Fixture()
        f.device.csc = "0"
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, null))
        f.device.cscWrite = { f.device.csc = "garbage" }
        assertTrue(runCatching { f.backend().restore() }.exceptionOrNull() is ShutterVerificationException)
        assertNotNull(f.store.journal.change)
    }
    @Test fun aFailedDefaultRestorationRetainsOriginalAndNeverSwitchesMethods() {
        val f = Fixture()
        f.device.csc = "0"
        f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
        f.device.cscWrite = { throw ShutterWriteUncertainException() }
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 0), f.store.journal.change)
        assertEquals(listOf("csc:1"), f.device.writes)
        f.device.cscWrite = null
        f.bootCount = 2 // A timed-out SettingsProvider transaction must not survive recovery.
        f.backend().restore()
        assertEquals("1", f.device.csc)
        assertNull(f.store.journal.change)
    }
    @Test fun defaultZeroReleaseAlreadyMatchesFactoryAndDoesNotRetainAChangedRecord() {
        val f = Fixture()
        f.device.audio = 0
        f.device.cscInfo = GalaxyCscSetting(true, 1, 0, true)
        val after = f.backend().release()
        assertEquals(0, after.systemDefault)
        assertFalse(after.changed)
        assertNull(f.store.journal.change)
    }
    @Test fun toggleRestoresAnotherToolsZeroAndPausesAutomaticMaintenance() = runBlocking {
        val f = Fixture()
        f.device.audio = 0
        f.device.csc = "0"
        f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
        val controller = f.controller(AutoMaintainState(true, true))
        assertEquals(0, controller.query())
        assertTrue(f.published.last().changed)
        assertFalse(f.published.last().hasChange)
        assertEquals(11, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertEquals(listOf("csc:1"), f.device.writes)
    }
    @Test fun oldAudioTransactionsStayPinnedDespiteTrustedCscMetadata() {
        val f = Fixture()
        f.device.audio = 0
        f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.AUDIO_POLICY, 11))
        f.device.audioWrite = { assertEquals(0, f.device.cscInfoReads); f.device.audio = it }
        f.backend().restore()
        assertEquals(listOf("audio:11"), f.device.writes)
    }
    @Test fun nonSamsungUsesAudioEvenWhenACscKeyExists() {
        val f = Fixture(samsung = false)
        assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().release().method)
        assertEquals(listOf("audio:0"), f.device.writes)
        assertEquals(0, f.device.cscInfoReads)
    }
    @Test fun alreadyReleasedNonSamsungCanRestoreWithoutAnExistingChangeRecord() {
        for (initialized in listOf(false, true)) {
            val f = Fixture(samsung = false)
            f.store.journal = ShutterJournal(initialized)
            f.device.audio = 0
            f.device.beforeCscRead = { fail("Non-Samsung must not read CSC") }
            val state = f.backend().inspect()
            assertTrue(state.hasChange)
            assertTrue(state.changed)
            assertEquals(11, state.baseline)
            assertTrue(f.device.writes.isEmpty())
            f.device.audioWrite = {
                assertEquals(ShutterChange(ShutterMethod.AUDIO_POLICY, 11), f.store.journal.change)
                f.device.audio = it
            }
            val restored = f.backend().restore()
            assertEquals(11, restored.policyValue)
            assertFalse(restored.hasChange)
            assertNull(f.store.journal.change)
            assertEquals(listOf("audio:11"), f.device.writes)
        }
    }
    @Test fun tileToggleRestoresAnUnrecordedAudioReleaseAndPausesMaintenance() = runBlocking {
        val f = Fixture(samsung = false)
        f.store.journal = ShutterJournal(true)
        f.device.audio = 0
        val controller = f.controller(AutoMaintainState(true, true))
        assertEquals(0, controller.query())
        assertTrue(f.published.last().changed)
        assertEquals(11, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertEquals(11, controller.query())
        assertFalse(f.published.last().changed)
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertEquals(listOf("audio:11"), f.device.writes)
    }
    @Test fun samsungWithKnownCscUsesGalaxyAndMapsOnlyAtTheUiBoundary() {
        val f = Fixture()
        val before = f.backend().inspect()
        assertEquals(1, before.rawValue)
        assertEquals(11, before.policyValue)
        assertEquals(ShutterMethod.GALAXY_CSC, before.method)
        val after = f.backend().release()
        assertEquals(Enforcement.RELEASED, after.enforcement)
        assertTrue(after.persistent)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(11, f.device.audio)
    }
    @Test fun absentUnknownUnreadableAndUnwritableCscNeverFallBackToAudio() {
        for (kind in 0..4) {
            val f = Fixture()
            when (kind) {
                0 -> f.device.csc = null
                1 -> f.device.csc = "2"
                2 -> f.device.csc = "garbage"
                3 -> f.device.cscReadable = false
                4 -> f.device.permission = false
            }
            val result = runCatching { f.backend().release() }
            if (kind == 0) assertTrue(result.getOrThrow().cscAbsent) else assertTrue(result.isFailure)
            assertTrue(f.device.writes.isEmpty())
        }
    }
    @Test fun audioDefaultNoneAndCurrentNoneCannotHideCscOne() {
        val f = Fixture(default = 0)
        f.device.audio = 0
        assertEquals(Enforcement.ENFORCED, f.backend().inspect().enforcement)
        f.backend().release()
        f.backend().restore()
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun defaultNoneDoesNotMaskAnActuallyEnforcedAudioPolicy() {
        val f = Fixture(default = 0)
        assertEquals(ShutterMethod.GALAXY_CSC, f.backend().release().method)
        assertEquals(listOf("csc:0"), f.device.writes)
    }
    @Test fun initialCscZeroDoesNotTouchIndependentAudioEnforcement() {
        val f = Fixture()
        f.device.csc = "0"
        f.backend().release()
        f.backend().restore()
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
        assertEquals(11, f.device.audio)
    }
    @Test fun alreadyReleasedAudioAndCscAreNotChangedByRelease() {
        val f = Fixture()
        f.device.audio = 0
        f.device.csc = "0"
        f.backend().release()
        assertTrue(f.device.writes.isEmpty())
    }
    @Test fun releaseThenRestoreUsesTheSavedCscValue() {
        val f = Fixture()
        f.backend().release()
        assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 1), f.store.journal.change)
        val restored = f.backend().restore()
        assertEquals("1", f.device.csc)
        assertFalse(restored.hasChange)
        assertNull(f.store.journal.change)
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun repeatedReleaseAndANewBackendInstanceKeepTheFirstRestorationValue() {
        val f = Fixture()
        f.backend().release()
        repeat(3) { f.backend().release() }
        assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 1), f.store.journal.change)
        f.backend().restore()
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun pinnedGalaxyDoesNotSwitchWhenCscPermissionOrAudioStateChanges() {
        val f = Fixture()
        f.backend().release()
        f.device.permission = false
        f.device.audio = 0
        assertEquals(ShutterMethod.GALAXY_CSC, f.backend().inspect().method)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
    }
    @Test fun legacyAudioReleaseRemainsRestorableThenNextReleaseSelectsGalaxy() {
        val f = Fixture(legacy = true)
        f.device.audio = 0
        assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().inspect().method)
        f.backend().restore()
        assertEquals(listOf("audio:11"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.backend().release().method)
        assertEquals(listOf("audio:11", "csc:0"), f.device.writes)
    }
    @Test fun legacyMaintenanceAfterRebootOnlyRestoresOldAudioUntilOptedIn() = runBlocking {
        val f = Fixture(legacy = true)
        val controller = f.controller(AutoMaintainState(true, true))
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertTrue(f.device.writes.isEmpty())
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterMethodChangeBlocked)
        controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11)
        assertNull(f.store.journal.change)
        assertEquals(ShutterMethod.GALAXY_CSC, f.backend().release().method)
    }
    @Test fun freshSamsungCscEnforcementIsNotHiddenByReleasedAudioPolicy() {
        val f = Fixture()
        f.device.audio = 0
        assertEquals(Enforcement.ENFORCED, f.backend().inspect().enforcement)
        f.backend().release()
        f.backend().restore()
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun initialCscAndAudioZeroWithoutLegacyIntentAreNotRestored() {
        val f = Fixture()
        f.device.audio = 0
        f.device.csc = "0"
        f.backend().release()
        f.backend().restore()
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
    }
    @Test fun savingRestorationInformationMustSucceedBeforeWriting() {
        val f = Fixture()
        f.backend().inspect()
        f.store.fail = true
        assertTrue(runCatching { f.backend().release() }.isFailure)
        assertTrue(f.device.writes.isEmpty())
    }
    @Test fun restorationRecordExistsBeforeTheDeviceWriteStarts() {
        val f = Fixture()
        f.device.cscWrite = {
            assertEquals(ShutterChange(ShutterMethod.GALAXY_CSC, 1), f.store.journal.change)
            f.device.csc = it.toString()
        }
        f.backend().release()
    }
    @Test fun failedCscWriteRetainsTheCscJournalWithoutAudioFallback() {
        val f = Fixture()
        f.device.cscWrite = { throw SecurityException("denied") }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
    }
    @Test fun silentlyIgnoredCscWriteRetainsTheCscJournalWithoutAudioFallback() {
        val f = Fixture()
        f.device.cscWrite = { }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertNotNull(f.store.journal.change)
    }
    @Test fun errorAfterAnEffectiveWriteIsVerifiedWithoutFallback() {
        val f = Fixture()
        f.device.cscWrite = { f.device.csc = "0"; throw IllegalStateException("late error") }
        assertEquals(Enforcement.RELEASED, f.backend().release().enforcement)
        assertEquals(listOf("csc:0"), f.device.writes)
    }
    @Test fun unknownCscAfterWriteKeepsGalaxyRecordAndDoesNotFallBack() {
        val f = Fixture()
        f.device.cscWrite = { f.device.csc = "2" }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
    }
    @Test fun aTimedOutWriteCannotFallBackEvenIfTheOriginalValueIsStillReadable() {
        val f = Fixture()
        f.device.cscWrite = { throw ShutterWriteUncertainException() }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertEquals("1", f.device.csc)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
    }
    @Test fun readbackFailureKeepsRecordAndNeverFallsBack() {
        val f = Fixture()
        f.device.cscWrite = { f.device.csc = "0"; f.device.cscReadable = false }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
        f.device.cscReadable = true
        f.device.cscWrite = null
        f.backend().restore()
        assertEquals("1", f.device.csc)
    }
    @Test fun interruptedBeforeOrAfterWriteCanBeRestoredWithoutChangingMethods() {
        for (raw in listOf("1", "0")) {
            val f = Fixture()
            f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
            f.device.csc = raw
            f.backend().restore()
            assertEquals("1", f.device.csc)
            assertEquals(if (raw == "0") listOf("csc:1") else emptyList<String>(), f.device.writes)
            assertNull(f.store.journal.change)
        }
    }
    @Test fun restoreReadbackFailureRetainsTheOriginalAndCanBeRetried() {
        val f = Fixture()
        f.backend().release()
        f.device.cscWrite = { f.device.csc = "1"; f.device.cscReadable = false }
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals(1, f.store.journal.change?.original)
        f.device.cscReadable = true
        f.backend().restore()
        assertNull(f.store.journal.change)
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun failedRecordClearAfterRestorationKeepsRecoveryInformation() {
        val f = Fixture()
        f.backend().release()
        f.device.cscWrite = { f.device.csc = it?.toString(); f.store.fail = true }
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals("1", f.device.csc)
        assertNotNull(f.store.journal.change)
        f.store.fail = false
        f.device.cscWrite = null
        f.bootCount = 2
        f.backend().restore()
        assertNull(f.store.journal.change)
    }
    @Test fun audioWriteAndRestoreAreReadBackToo() {
        val f = Fixture(samsung = false)
        f.device.audioWrite = { }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterVerificationException)
        assertNotNull(f.store.journal.change)
    }
    @Test fun audioReleaseThatMatchesDefaultNoneNeedsNoChangeRecord() {
        val f = Fixture(samsung = false, default = 0)
        val state = f.backend().release()
        assertEquals(0, state.baseline)
        assertFalse(state.hasChange)
        assertNull(f.store.journal.change)
        assertEquals(listOf("audio:0"), f.device.writes)
    }
    @Test fun corruptOrMissingPinnedCscCannotFallBackDuringMaintenance() = runBlocking {
        val f = Fixture()
        f.backend().release()
        f.device.csc = null
        val controller = f.controller(AutoMaintainState(true, true))
        assertTrue(runCatching { controller.maintain() }.isFailure)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(ShutterMethod.GALAXY_CSC, f.store.journal.change?.method)
    }
    @Test fun galaxyWorksWhenAudioReadOrDeviceDefaultIsUnavailable() {
        val f = Fixture(default = null)
        f.device.audioReadable = false
        f.backend().release()
        f.backend().restore()
        assertEquals(listOf("csc:0", "csc:1"), f.device.writes)
    }
    @Test fun controllerMaintainsGalaxyOnlyWhenRevertedAndRestorePausesIt() = runBlocking {
        val f = Fixture()
        val controller = f.controller(AutoMaintainState(true, false))
        controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
        repeat(2) { assertEquals(MaintainResult.ALREADY_RELEASED, controller.maintain()) }
        f.device.csc = "1"
        assertEquals(MaintainResult.RESTORED, controller.maintain())
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertEquals(listOf("csc:0", "csc:0", "csc:1"), f.device.writes)
    }
    @Test fun sharedConnectionReportsIdenticalStateForQueryReleaseAndToggle() = runBlocking {
        val f = Fixture()
        val controller = f.controller()
        assertEquals(11, controller.query())
        assertEquals(0, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertEquals(0, controller.query())
        assertTrue(f.published.last().hasChange)
        assertEquals(11, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertFalse(f.published.last().hasChange)
        assertEquals(11, controller.query())
    }
    @Test fun aRevertedSettingMakesTheTileReleaseAgainWithoutLosingItsOriginal() = runBlocking {
        val f = Fixture()
        val controller = f.controller()
        controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
        f.device.csc = "1"
        assertEquals(11, controller.query())
        assertFalse(f.published.last().changed)
        assertEquals(0, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertEquals(1, f.store.journal.change?.original)
        assertTrue(f.published.last().changed)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
        assertEquals("1", f.device.csc)
    }
    @Test fun unknownReadbackPublishesUnknownAndRetainsRestorationForAllRoutes() = runBlocking {
        val f = Fixture()
        val controller = f.controller()
        f.device.cscWrite = { f.device.cscReadable = false }
        assertTrue(runCatching {
            controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
        }.isFailure)
        assertEquals(Enforcement.UNKNOWN, f.published.last().enforcement)
        assertTrue(f.published.last().hasChange)
    }
    @Test fun galaxyRestorationWorksWithoutAnAudioDefault() = runBlocking {
        val f = Fixture(default = null)
        val controller = f.controller()
        controller.performManual(controller.requestManual(ManualAction.RELEASE), null)
        controller.performManual(controller.requestManual(ManualAction.RESTORE), null)
        assertEquals("1", f.device.csc)
    }
    @Test fun nonSamsungDefaultNoneTileKeepsItsExistingMaintenanceIntentBehavior() = runBlocking {
        val f = Fixture(samsung = false, default = 0)
        f.device.audio = 0
        val controller = f.controller(AutoMaintainState(true, false))
        assertEquals(0, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 0))
        assertTrue(controller.state.value.armed)
        assertEquals(MaintainResult.ALREADY_RELEASED, controller.maintain())
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun conflictingPresenceMetadataIsUnknownAndCannotCreateAKey() {
        val f = Fixture()
        f.device.cscInfo = GalaxyCscSetting(false, null)
        assertEquals(Enforcement.UNKNOWN, f.backend().inspect().enforcement)
        assertTrue(runCatching { f.backend().release() }.isFailure)
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun absentKeyTileDoesNotNeedNonMainUserConsentOrPerformARelease() = runBlocking {
        val f = Fixture()
        f.device.csc = null
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 95), object : SamsungUserConsentStore {
            override fun load() = SamsungUserConsent()
            override fun save(consent: SamsungUserConsent) { fail("No confirmation should be recorded") }
        })
        val controller = AutoMaintainController(object : AutoMaintainStore {
            override fun load() = AutoMaintainState()
            override fun save(state: AutoMaintainState) {}
        }, f.connector(), releaseAuthorization = warning)
        assertEquals(0, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
        assertTrue(warning.confirmationRequired)
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun anOldAudioJournalNeverAutomaticallyReleasesInNormalModeWithAnAbsentKey() = runBlocking {
        val f = Fixture()
        f.device.csc = null
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.AUDIO_POLICY, 11))
        val controller = f.controller(AutoMaintainState(true, true))
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
        assertEquals(ShutterMethod.AUDIO_POLICY, f.store.journal.change!!.method)
        assertTrue(f.device.writes.isEmpty())
        controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11)
        assertNull(f.store.journal.change)
        assertTrue(f.published.last().cscAbsent)
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun absentCscIgnoresBothAudioValuesAcrossQueryTileAndAutomaticRoutes() = runBlocking {
        for (audio in listOf(0, 11)) {
            val f = Fixture()
            f.device.csc = null
            f.device.audio = audio
            f.device.audioReadable = false // Ordinary Galaxy mode must not even consult AudioPolicy.
            val controller = f.controller(AutoMaintainState(true, true))
            assertEquals(0, controller.query())
            val snapshot = f.published.last()
            assertTrue(snapshot.cscAbsent)
            assertNull(snapshot.rawValue)
            assertEquals(0, snapshot.baseline)
            assertFalse(snapshot.restorable)
            assertFalse(snapshot.changed)
            assertEquals(MaintainResult.ALREADY_RELEASED, controller.maintain())
            assertEquals(0, controller.performManual(controller.requestManual(ManualAction.TOGGLE), 11))
            assertEquals(MaintainResult.INACTIVE, controller.maintain())
            assertTrue(f.device.writes.isEmpty())
            assertNull(f.store.journal.change)
        }
    }

    @Test fun failedReadsPresentNullAndUnknownValuesRemainUnknownAcrossRoutes() = runBlocking {
        for (kind in 0..3) {
            val f = Fixture()
            when (kind) {
                0 -> f.device.cscReadable = false
                1 -> { f.device.csc = null; f.device.cscInfo = GalaxyCscSetting(true, null) }
                2 -> { f.device.csc = null; f.device.cscInfoReadable = false }
                3 -> f.device.csc = "2"
            }
            val state = f.backend().inspect()
            assertEquals(Enforcement.UNKNOWN, state.enforcement)
            assertFalse(state.cscAbsent)
            assertNull(state.rawValue)
            val controller = f.controller(AutoMaintainState(true, true))
            assertTrue(runCatching { controller.query() }.exceptionOrNull() is ShutterVerificationException)
            assertTrue(runCatching { controller.maintain() }.isFailure)
            assertTrue(f.device.writes.isEmpty())
            assertNull(f.store.journal.change)
        }
    }

    @Test fun explicitAudioModeIgnoresCscAndCanMaintainAnAbsentKeyGalaxy() = runBlocking {
        val f = Fixture(audioMode = true)
        f.device.csc = null
        f.device.cscReadable = false
        val controller = f.controller(AutoMaintainState(true, true))
        assertEquals(11, controller.query())
        assertEquals(MaintainResult.RESTORED, controller.maintain())
        assertEquals(ShutterMethod.AUDIO_POLICY, f.store.journal.change!!.method)
        assertEquals(MaintainResult.ALREADY_RELEASED, controller.maintain())
        f.device.audio = 11
        assertEquals(MaintainResult.RESTORED, controller.maintain())
        assertEquals(11, controller.performManual(controller.requestManual(ManualAction.RESTORE), 11))
        assertEquals(listOf("audio:0", "audio:0", "audio:11"), f.device.writes)
        assertEquals(0, f.device.cscInfoReads)
    }

    @Test fun journaledAudioCanRestoreOnAbsentCscAndNeverSwitchesDuringAFailedRestore() {
        val f = Fixture()
        f.device.csc = null
        f.device.audio = 0
        val original = ShutterChange(ShutterMethod.AUDIO_POLICY, 11)
        f.store.journal = ShutterJournal(true, original)
        f.device.audioWrite = { throw SecurityException("denied") }
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals(original, f.store.journal.change)
        assertEquals(listOf("audio:11"), f.device.writes)
        f.device.audioWrite = null
        val state = f.backend().restore()
        assertTrue(state.cscAbsent)
        assertEquals(11, f.device.audio)
        assertNull(f.store.journal.change)
        assertEquals(listOf("audio:11", "audio:11"), f.device.writes)
    }

    @Test fun modeSwitchRequiresVerifiedRestorationAndPausesMaintenance() = runBlocking {
        for (audioMode in listOf(false, true)) {
            val f = Fixture(audioMode = audioMode)
            val controller = f.controller(AutoMaintainState(true, true))
            controller.performManual(controller.requestManual(ManualAction.RELEASE), 11)
            val original = f.store.journal.change
            assertTrue(runCatching {
                controller.updateControlMethod(f.store) { f.audioMode = !audioMode }
            }.exceptionOrNull() is ShutterMethodChangeBlocked)
            assertEquals(audioMode, f.audioMode)
            assertEquals(original, f.store.journal.change)
            controller.performManual(controller.requestManual(ManualAction.RESTORE), 11)
            controller.updateControlMethod(f.store) { f.audioMode = !audioMode }
            assertEquals(!audioMode, f.audioMode)
            assertFalse(controller.state.value.armed)
            assertEquals(MaintainResult.INACTIVE, controller.maintain())
            assertEquals(if (audioMode) ShutterMethod.GALAXY_CSC else ShutterMethod.AUDIO_POLICY,
                f.backend().inspect().method)
        }
    }

    @Test fun modeSwitchCannotOvertakeAPendingManualRequest() = runBlocking {
        val f = Fixture()
        val controller = f.controller()
        val request = controller.requestManual(ManualAction.RELEASE)
        assertTrue(runCatching { controller.updateControlMethod(f.store) { f.audioMode = true } }.isFailure)
        assertFalse(f.audioMode)
        controller.performManual(request, 11)
        assertEquals(listOf("csc:0"), f.device.writes)
    }

    @Test fun aDisappearedCscKeyIsNotRecreatedAndItsJournalIsKept() {
        val f = Fixture()
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
        f.device.csc = null
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals(1, f.store.journal.change!!.original)
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun workerCancellationDuringTheBackendReadCannotWriteLater() = runBlocking {
        val f = Fixture()
        f.backend().release()
        f.device.csc = "1"
        val controller = f.controller(AutoMaintainState(true, true))
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var reads = 0
        f.device.beforeCscRead = {
            if (++reads == 2) {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
            }
        }
        val worker = async(Dispatchers.IO) { controller.maintain() }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            worker.cancel()
            finish.countDown()
            worker.join()
            assertTrue(worker.isCancelled)
            assertEquals(listOf("csc:0"), f.device.writes)
            assertEquals(1, f.store.journal.change?.original)
        } finally {
            finish.countDown()
            worker.cancelAndJoin()
        }
    }
    @Test fun lateReleaseCannotLoseItsRestorationRecordOrReportRestoredBeforeReboot() {
        val f = Fixture()
        var delayedWrite: (() -> Unit)? = null
        f.device.cscWrite = { target ->
            delayedWrite = { f.device.csc = target?.toString() }
            throw ShutterWriteUncertainException()
        }
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterWritePendingException)
        assertEquals("1", f.device.csc)
        // The old value is readable, but the original write can still complete.
        assertTrue(runCatching { f.backend().restore() }.exceptionOrNull() is ShutterWritePendingException)
        delayedWrite!!.invoke()
        assertEquals("0", f.device.csc)
        assertEquals(1, f.store.journal.change?.original)
        assertEquals(1, f.store.journal.pendingCscWriteBoot)
        // Recreating the backend does not discard the pending marker.
        assertTrue(runCatching { f.backend().inspect() }.exceptionOrNull() is ShutterWritePendingException)
        f.bootCount = 2
        f.device.cscWrite = null
        f.backend().restore()
        assertEquals("1", f.device.csc)
        assertNull(f.store.journal.change)
        assertNull(f.store.journal.pendingCscWriteBoot)
    }

    @Test fun interruptedCscWriteIsBlockedAcrossRestartsUntilANewBootCanVerifyIt() {
        val f = Fixture()
        f.store.journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1), 1)
        for (boot in listOf(1, null)) {
            f.bootCount = boot
            assertTrue(runCatching { f.backend().restore() }.exceptionOrNull() is ShutterWritePendingException)
            assertEquals(1, f.store.journal.pendingCscWriteBoot)
            assertTrue(f.device.writes.isEmpty())
        }
        f.bootCount = 2
        f.backend().restore()
        assertNull(f.store.journal.change)
        assertTrue(f.device.writes.isEmpty())
    }

    @Test fun unavailableBootInformationPreventsSubmittingAnUnrecoverableCscWrite() {
        val f = Fixture()
        f.bootCount = null
        assertTrue(runCatching { f.backend().release() }.exceptionOrNull() is ShutterBootUnavailableException)
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.pendingCscWriteBoot)
    }

    @Test fun cancellationAtTheFinalCheckClearsOnlyTheUnsubmittedPendingMarker() {
        val f = Fixture()
        assertTrue(runCatching {
            f.backend().release {
                assertEquals(1, f.store.journal.pendingCscWriteBoot)
                throw CancellationException("Stopped before IPC")
            }
        }.exceptionOrNull() is CancellationException)
        assertNull(f.store.journal.pendingCscWriteBoot)
        assertNotNull(f.store.journal.change)
        assertTrue(f.device.writes.isEmpty())
        f.backend().restore()
        assertNull(f.store.journal.change)
    }

    @Test fun offDuringTheBackendsSecondReadReturnsPromptlyAndPreventsAnAutomaticWrite() = runBlocking {
        val f = Fixture()
        val controller = f.controller(AutoMaintainState(true, true))
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var reads = 0
        f.device.beforeCscRead = {
            if (++reads == 2) { started.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
        }
        val worker = async(Dispatchers.IO) { controller.maintain() }
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val off = async(Dispatchers.Default) { controller.setEnabled(false) }
            withTimeout(1000) { off.await() }
            assertFalse(controller.state.value.enabled)
        } finally { finish.countDown() }
        assertEquals(MaintainResult.SUPERSEDED, worker.await())
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.pendingCscWriteBoot)
    }

    @Test fun newerRestoreDuringTheBackendsSecondReadReturnsPromptlyAndSupersedesRelease() = runBlocking {
        val f = Fixture()
        val controller = f.controller()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var reads = 0
        f.device.beforeCscRead = {
            if (++reads == 2) { started.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
        }
        val release = controller.requestManual(ManualAction.RELEASE)
        val work = async(Dispatchers.IO) { runCatching { controller.performManual(release, 11) } }
        val restore = try {
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val request = async(Dispatchers.Default) { controller.requestManual(ManualAction.RESTORE) }
            withTimeout(1000) { request.await() }
        } finally { finish.countDown() }
        assertTrue(work.await().exceptionOrNull() is SupersededPolicyOperation)
        f.device.beforeCscRead = null
        controller.performManual(restore, 11)
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
    }

    @Test fun manualRestoreSupersedesAnAutomaticGalaxyReadBeforeWriting() = runBlocking {
        val f = Fixture()
        f.backend().release()
        f.device.csc = "1"
        val controller = f.controller(AutoMaintainState(true, true))
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        f.device.beforeCscRead = { started.countDown(); check(finish.await(3, TimeUnit.SECONDS)) }
        val worker = async(Dispatchers.IO) { controller.maintain() }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        val restore = controller.requestManual(ManualAction.RESTORE)
        finish.countDown()
        assertEquals(MaintainResult.SUPERSEDED, worker.await())
        f.device.beforeCscRead = null
        controller.performManual(restore, 11)
        assertEquals(listOf("csc:0"), f.device.writes)
        assertEquals(MaintainResult.INACTIVE, controller.maintain())
    }
}
