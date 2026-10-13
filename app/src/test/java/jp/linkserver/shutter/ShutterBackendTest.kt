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
        override fun readCscInfo(): GalaxyCscSetting? { cscInfoReads++; check(cscInfoReadable); return cscInfo }
        override fun writeCsc(value: Int?) {
            writes += "csc:$value"
            cscWrite?.invoke(value) ?: run { csc = value?.toString() }
        }
        override fun canWriteCsc() = permission
    }
    private class Fixture(samsung: Boolean = true, default: Int? = 11, legacy: Boolean = false) {
        val store = Store()
        val device = Device()
        private val samsung = samsung
        private val default = default
        private val legacy = legacy
        fun backend() = ShutterBackend(device, store, samsung, default, legacy)
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
        for (kind in 0..3) {
            val f = Fixture()
            when (kind) {
                0 -> f.device.cscInfo = null
                1 -> f.device.cscInfo = GalaxyCscSetting(true, 1, 0, false)
                2 -> f.device.cscInfoReadable = false
                3 -> f.device.cscInfo = GalaxyCscSetting(false, null)
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
    @Test fun metadataAloneDoesNotSelectAnAbsentOrUnwritableCscKey() {
        for (absent in listOf(true, false)) {
            val f = Fixture()
            f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
            if (absent) f.device.csc = null else f.device.permission = false
            assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().release().method)
            assertEquals(listOf("audio:0"), f.device.writes)
        }
    }
    @Test fun releaseNeverSetsAnInitialZeroToOneEvenWithDefaultOne() {
        val f = Fixture()
        f.device.csc = "0"
        f.device.cscInfo = GalaxyCscSetting(true, 0, 1, true)
        f.backend().release()
        assertEquals(listOf("audio:0"), f.device.writes)
        assertEquals("0", f.device.csc)
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
    @Test fun absentUnknownUnreadableOrUnwritableCscFallsBackToAudio() {
        for (kind in 0..4) {
            val f = Fixture()
            when (kind) {
                0 -> f.device.csc = null
                1 -> f.device.csc = "2"
                2 -> f.device.csc = "garbage"
                3 -> f.device.cscReadable = false
                4 -> f.device.permission = false
            }
            assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().release().method)
            assertEquals(listOf("audio:0"), f.device.writes)
        }
    }
    @Test fun defaultNoneAndCurrentNoneDoNotEvenReadOrChangeCsc() {
        val f = Fixture(default = 0)
        f.device.audio = 0
        f.device.beforeCscRead = { fail("CSC must not be probed") }
        val state = f.backend().release()
        assertEquals(0, state.baseline)
        assertFalse(state.hasChange)
        f.backend().restore()
        assertTrue(f.device.writes.isEmpty())
        assertNull(f.store.journal.change)
        assertEquals(0, f.device.cscInfoReads)
    }
    @Test fun defaultNoneDoesNotMaskAnActuallyEnforcedAudioPolicy() {
        val f = Fixture(default = 0)
        assertEquals(ShutterMethod.GALAXY_CSC, f.backend().release().method)
        assertEquals(listOf("csc:0"), f.device.writes)
    }
    @Test fun initialCscZeroUsesAudioForIndependentEnforcementAndNeverWritesCsc() {
        val f = Fixture()
        f.device.csc = "0"
        f.backend().release()
        f.backend().restore()
        assertEquals(listOf("audio:0", "audio:11"), f.device.writes)
        assertEquals("0", f.device.csc)
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
    @Test fun legacyMaintenanceAfterRebootRetainsAudioUntilRestored() {
        val f = Fixture(legacy = true)
        assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().release().method)
        f.backend().restore()
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
    @Test fun failedCscWriteFallsBackOnlyAfterConfirmedUnchangedRead() {
        val f = Fixture()
        f.device.cscWrite = { throw SecurityException("denied") }
        assertEquals(ShutterMethod.AUDIO_POLICY, f.backend().release().method)
        assertEquals(listOf("csc:0", "audio:0"), f.device.writes)
        assertEquals(ShutterMethod.AUDIO_POLICY, f.store.journal.change?.method)
    }
    @Test fun silentlyIgnoredCscWriteAlsoRequiresConfirmedUnchangedRead() {
        val f = Fixture()
        f.device.cscWrite = { }
        f.backend().release()
        assertEquals(listOf("csc:0", "audio:0"), f.device.writes)
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
        f.store.fail = true
        assertTrue(runCatching { f.backend().restore() }.isFailure)
        assertEquals("1", f.device.csc)
        assertNotNull(f.store.journal.change)
        f.store.fail = false
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
