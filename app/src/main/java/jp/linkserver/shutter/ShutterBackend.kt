package jp.linkserver.shutter

import jp.linkserver.shutter.maintain.AudioPolicyConnection

/** Raw CSC values never escape this backend as audio-policy values. */
internal enum class ShutterMethod { AUDIO_POLICY, GALAXY_CSC }
internal enum class Enforcement { ENFORCED, RELEASED, UNKNOWN }
internal data class ShutterChange(val method: ShutterMethod, val original: Int?)
internal data class ShutterJournal(
    val initialized: Boolean = false,
    val change: ShutterChange? = null,
    val pendingCscWriteBoot: Int? = null
)
internal interface ShutterChangeStore {
    fun load(): ShutterJournal
    /** Must be durable before any device setting is written. */
    fun save(journal: ShutterJournal)
}

/** The single adapter shared by home, tile, notification and maintenance routes. */
internal class BackendPolicyConnection(
    private val backend: ShutterBackend,
    private val store: ShutterChangeStore,
    private val publish: (ShutterSnapshot) -> Unit
) : AudioPolicyConnection {
    private var snapshot: ShutterSnapshot? = null
    private fun perform(operation: () -> ShutterSnapshot): Int = try {
        val value = operation()
        snapshot = value
        publish(value)
        if (value.persistent && value.enforcement == Enforcement.UNKNOWN && value.restoredToSystemDefault == null)
            throw ShutterVerificationException()
        value.policyValue
    } catch (error: Exception) {
        store.load().change?.let { change ->
            val unknown = ShutterSnapshot(change.method, Enforcement.UNKNOWN, null, change.original, true)
            snapshot = unknown
            publish(unknown)
        }
        throw error
    }

    override val canRelease get() = snapshot?.releaseAllowed ?: true
    override val releaseWouldChangeSetting get() = snapshot?.let {
        !(it.persistent && it.enforcement == Enforcement.RELEASED && !it.restorable)
    } ?: true
    override fun read() = perform(backend::inspect)
    override fun write(value: Int) { error("Use the explicit release/restore operations") }
    override fun baseline(deviceDefault: Int?) = snapshot?.baseline ?: deviceDefault
    override fun shouldRestore(current: Int, deviceDefault: Int?) =
        snapshot?.let { it.restorable && (!it.releaseAllowed || it.changed || it.enforcement == Enforcement.RELEASED) }
            ?: (current != baseline(deviceDefault))
    override fun release(beforeWrite: () -> Unit) = perform { backend.release(beforeWrite) }
    override fun restore(deviceDefault: Int?, beforeWrite: () -> Unit) = perform { backend.restore(beforeWrite) }
}
internal interface ShutterDevice {
    fun readAudio(): Int
    fun writeAudio(value: Int)
    fun readCsc(): String?
    fun readCscInfo(): GalaxyCscSetting? = null
    fun writeCsc(value: Int?)
    fun canWriteCsc(): Boolean
}

internal data class ShutterSnapshot(
    val method: ShutterMethod,
    val enforcement: Enforcement,
    val rawValue: Int?,
    val restoreValue: Int?,
    val hasChange: Boolean,
    val systemDefault: Int? = null,
    val currentKnown: Boolean = rawValue != null,
    val restoredToSystemDefault: Boolean? = null,
    val cscAbsent: Boolean = false,
    val releaseAllowed: Boolean = true
) {
    val persistent get() = method == ShutterMethod.GALAXY_CSC
    // Compatibility boundary for the existing UI/coordinator. These are logical
    // states here, not the raw CSC value (in particular CSC 1 is never exposed).
    val policyValue: Int get() = when (enforcement) {
        Enforcement.RELEASED -> ForceUse.FORCE_NONE
        Enforcement.ENFORCED -> ForceUse.FORCE_SYSTEM_ENFORCED
        Enforcement.UNKNOWN -> if (method == ShutterMethod.AUDIO_POLICY) rawValue ?: -1 else -1
    }
    val baseline: Int? get() = if (cscAbsent) ForceUse.FORCE_NONE else restoreValue?.let {
        if (method == ShutterMethod.GALAXY_CSC) {
            if (it == 1) ForceUse.FORCE_SYSTEM_ENFORCED else ForceUse.FORCE_NONE
        } else it
    }
    val restorable get() = hasChange || persistent && systemDefault != null && currentKnown && rawValue != restoreValue
    val changed get() = hasChange && (!currentKnown || rawValue != restoreValue) ||
        persistent && systemDefault != null && currentKnown && rawValue != restoreValue
}

internal open class ShutterVerificationException(cause: Throwable? = null) :
    IllegalStateException("Could not verify the shutter setting; the restoration record was retained", cause)

internal class ShutterWritePendingException(cause: Throwable? = null) : ShutterVerificationException(cause)
internal class ShutterBootUnavailableException : IllegalStateException("The device boot count is unavailable")

/** A killed/timed-out client can still have a write pending in SettingsProvider. */
internal class ShutterWriteUncertainException(cause: Throwable? = null) :
    IllegalStateException("The settings write may still complete", cause)

/** Device operations are serialized by AutoMaintainController's operation mutex. */
internal class ShutterBackend(
    private val device: ShutterDevice,
    private val store: ShutterChangeStore,
    private val samsung: Boolean,
    private val deviceDefault: Int?,
    private val legacyMaintainArmed: Boolean = false,
    private val forceAudioPolicy: Boolean = false,
    private val bootCount: Int? = null
) {
    fun inspect(): ShutterSnapshot {
        val journal = store.load()
        journal.pendingCscWriteBoot?.let { pendingBoot ->
            // A read can overtake a timed-out SettingsProvider write. Only a new
            // boot guarantees that no write from that transaction is still pending.
            if (bootCount == null || bootCount == pendingBoot) throw ShutterWritePendingException()
            store.save(journal.copy(pendingCscWriteBoot = null))
        }
        journal.change?.let { return readPinned(it) }
        val audioMode = !samsung || forceAudioPolicy
        // Only migration evidence may recover an unjournaled old Galaxy audio
        // transaction. Ordinary Galaxy detection never consults AudioPolicy.
        val recoverLegacy = !journal.initialized && legacyMaintainArmed
        if (audioMode || recoverLegacy) {
            val audio = device.readAudio()
            if (audio == ForceUse.FORCE_NONE && deviceDefault == ForceUse.FORCE_SYSTEM_ENFORCED ||
                recoverLegacy && legacyMaintainArmed && deviceDefault == ForceUse.FORCE_SYSTEM_ENFORCED &&
                audio == ForceUse.FORCE_SYSTEM_ENFORCED) {
                val existing = ShutterChange(ShutterMethod.AUDIO_POLICY, ForceUse.FORCE_SYSTEM_ENFORCED)
                store.save(ShutterJournal(true, existing))
                return readPinned(existing)
            }
        }
        if (!journal.initialized) store.save(ShutterJournal(true))
        if (audioMode) return audioSnapshot(device.readAudio(), null)
        val reading = readCsc()
        if (reading.absent) return ShutterSnapshot(ShutterMethod.GALAXY_CSC, Enforcement.RELEASED,
            null, null, false, currentKnown = true, cscAbsent = true)
        return snapshot(ShutterMethod.GALAXY_CSC, reading.value, reading.default ?: reading.value,
            false, reading.default, reading.known)
    }

    fun release(beforeWrite: () -> Unit = {}): ShutterSnapshot {
        val before = inspect()
        if (!before.releaseAllowed) throw ShutterMethodChangeBlocked()
        if (before.enforcement == Enforcement.RELEASED) return before
        check(before.enforcement == Enforcement.ENFORCED) { "Unknown shutter setting" }
        val previous = store.load().change
        val change = previous ?: ShutterChange(before.method,
            if (before.persistent) before.rawValue else checkNotNull(before.restoreValue))
        if (previous == null) store.save(ShutterJournal(true, change))
        // Extra reads and durable saving can outlast a Worker cancellation.
        // Recheck immediately before each write, outside write-failure recovery.
        val failure = attemptWrite(change, releasedValue(change.method), beforeWrite)
        val after = try { readPinned(change) }
        catch (error: Exception) { throw ShutterVerificationException(error) }
        if (after.enforcement == Enforcement.RELEASED) return finishRelease(after)
        throw ShutterVerificationException(failure)
    }

    private fun finishRelease(after: ShutterSnapshot): ShutterSnapshot {
        // Audio may have been forced transiently on a default-none device. The
        // release already matches its restore target, so no changed-state record
        // or persistent notification is needed (the legacy behavior).
        if (after.rawValue == after.restoreValue) {
            store.save(ShutterJournal(true))
            return inspect()
        }
        return after
    }

    fun restore(beforeWrite: () -> Unit = {}): ShutterSnapshot {
        // Includes pre-upgrade audio recovery, and a CSC default discovered
        // after reinstalling or after another tool changed the current value.
        val before = inspect()
        if (!before.restorable) return before
        check(before.currentKnown) { "Unknown shutter setting" }
        val change = store.load().change ?: ShutterChange(before.method, before.rawValue).also {
            store.save(ShutterJournal(true, it))
        }
        // A fresh trustworthy system default takes precedence over EVERY saved
        // current value. Without it only our saved change can be undone.
        val target = before.restoreValue
        if (before.rawValue != target) {
            val failure = attemptWrite(change, target, beforeWrite)
            val after = try { readPinned(change) }
            catch (error: Exception) { throw ShutterVerificationException(error) }
            if (!after.currentKnown || after.rawValue != target) throw ShutterVerificationException(failure)
        }
        // Clear only after confirmed restoration. A save failure keeps the record
        // recoverable, and manual restore has already paused automatic work.
        store.save(ShutterJournal(true))
        return inspect().copy(restoredToSystemDefault = !before.persistent || before.systemDefault != null)
    }

    private fun readPinned(change: ShutterChange): ShutterSnapshot = when (change.method) {
        ShutterMethod.AUDIO_POLICY -> audioSnapshot(device.readAudio(), change)
            .copy(releaseAllowed = !samsung || forceAudioPolicy)
        ShutterMethod.GALAXY_CSC -> {
            val reading = readCsc()
            // Never recreate a missing key, even to restore a recorded value.
            // A saved absent original can still be verified and cleared.
            snapshot(change.method, reading.value, reading.default ?: change.original, true, reading.default,
                reading.known && (!reading.absent || change.original == null)).let {
                    if (reading.absent && change.original == null)
                        it.copy(enforcement = Enforcement.RELEASED, cscAbsent = true) else it
                }
        }
    }

    private data class CscReading(val value: Int?, val default: Int?, val known: Boolean, val absent: Boolean = false)
    private fun readCsc(): CscReading {
        val raw = try { device.readCsc() } catch (_: Exception) { return CscReading(null, null, false) }
        val info = runCatching { device.readCscInfo() }.getOrNull()
        if (raw == null) return CscReading(null, null, info?.exists == false, info?.exists == false)
        if (info?.exists == false) return CscReading(null, null, false)
        val value = raw.toIntOrNull()?.takeIf { raw == "0" || raw == "1" }
        return CscReading(value, info?.takeIf { it.exists }?.trustedDefault, value != null)
    }

    private fun audioSnapshot(value: Int, change: ShutterChange?) =
        snapshot(ShutterMethod.AUDIO_POLICY, value, change?.original ?: deviceDefault ?: value, change != null)

    private fun snapshot(method: ShutterMethod, raw: Int?, original: Int?, changed: Boolean,
        systemDefault: Int? = null, currentKnown: Boolean = raw != null): ShutterSnapshot {
        val state = when (raw) {
            releasedValue(method) -> Enforcement.RELEASED
            if (method == ShutterMethod.GALAXY_CSC) 1 else ForceUse.FORCE_SYSTEM_ENFORCED -> Enforcement.ENFORCED
            else -> Enforcement.UNKNOWN
        }
        return ShutterSnapshot(method, state, raw, original, changed, systemDefault, currentKnown)
    }

    private fun releasedValue(method: ShutterMethod) =
        if (method == ShutterMethod.GALAXY_CSC) 0 else ForceUse.FORCE_NONE
    private fun attemptWrite(change: ShutterChange, value: Int?, beforeWrite: () -> Unit): Throwable? {
        if (change.method == ShutterMethod.AUDIO_POLICY) {
            beforeWrite()
            return runCatching { device.writeAudio(checkNotNull(value)) }.exceptionOrNull()
        }
        check(device.canWriteCsc()) { "CSC write permission is unavailable" }
        val boot = bootCount ?: throw ShutterBootUnavailableException()
        val settled = ShutterJournal(true, change)
        // Persist before IPC, including for process death between submission and
        // acknowledgement. A later client must not infer completion from a read.
        store.save(settled.copy(pendingCscWriteBoot = boot))
        try { beforeWrite() }
        catch (error: Exception) {
            store.save(settled) // No write was submitted, so a reboot is unnecessary.
            throw error
        }
        val failure = runCatching { device.writeCsc(value) }.exceptionOrNull()
        if (failure is ShutterWriteUncertainException) throw ShutterWritePendingException(failure)
        // Other service errors are acknowledged command completions; readback
        // can verify their result. A missing acknowledgement keeps the marker.
        store.save(settled)
        return failure
    }
}
