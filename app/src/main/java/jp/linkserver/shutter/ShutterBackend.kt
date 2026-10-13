package jp.linkserver.shutter

import jp.linkserver.shutter.maintain.AudioPolicyConnection

/** Raw CSC values never escape this backend as audio-policy values. */
internal enum class ShutterMethod { AUDIO_POLICY, GALAXY_CSC }
internal enum class Enforcement { ENFORCED, RELEASED, UNKNOWN }
internal data class ShutterChange(val method: ShutterMethod, val original: Int?)
internal data class ShutterJournal(val initialized: Boolean = false, val change: ShutterChange? = null)
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
        if (value.persistent && value.enforcement == Enforcement.UNKNOWN)
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

    override fun read() = perform(backend::inspect)
    override fun write(value: Int) { error("Use the explicit release/restore operations") }
    override fun baseline(deviceDefault: Int?) = snapshot?.baseline ?: deviceDefault
    override fun shouldRestore(current: Int, deviceDefault: Int?) =
        snapshot?.let { it.restorable && (it.changed || it.enforcement == Enforcement.RELEASED) }
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
    val restoredToSystemDefault: Boolean? = null
) {
    val persistent get() = method == ShutterMethod.GALAXY_CSC
    // Compatibility boundary for the existing UI/coordinator. These are logical
    // states here, not the raw CSC value (in particular CSC 1 is never exposed).
    val policyValue: Int get() = when (enforcement) {
        Enforcement.RELEASED -> ForceUse.FORCE_NONE
        Enforcement.ENFORCED -> ForceUse.FORCE_SYSTEM_ENFORCED
        Enforcement.UNKNOWN -> if (method == ShutterMethod.AUDIO_POLICY) rawValue ?: -1 else -1
    }
    val baseline: Int? get() = restoreValue?.let {
        if (method == ShutterMethod.GALAXY_CSC) {
            if (it == 1) ForceUse.FORCE_SYSTEM_ENFORCED else ForceUse.FORCE_NONE
        } else it
    }
    val restorable get() = hasChange || persistent && systemDefault != null && currentKnown && rawValue != restoreValue
    val changed get() = (hasChange || persistent && systemDefault != null) && rawValue != restoreValue
}

internal class ShutterVerificationException(cause: Throwable? = null) :
    IllegalStateException("Could not verify the shutter setting; the restoration record was retained", cause)

/** A killed/timed-out client can still have a write pending in SettingsProvider. */
internal class ShutterWriteUncertainException(cause: Throwable? = null) :
    IllegalStateException("The settings write may still complete", cause)

/** Called only under AutoMaintainController's operation and intent locks. */
internal class ShutterBackend(
    private val device: ShutterDevice,
    private val store: ShutterChangeStore,
    private val samsung: Boolean,
    private val deviceDefault: Int?,
    private val legacyMaintainArmed: Boolean = false,
    private val legacyInstallation: Boolean = false
) {
    fun inspect(): ShutterSnapshot {
        var journal = store.load()
        journal.change?.let { return readPinned(it) }
        val audio = runCatching { device.readAudio() }
        // AudioPolicy can restore a known device default even without a prior
        // record. Recover non-Samsung releases after migration has run as well.
        // Samsung still requires legacy evidence so untouched CSC 0 stays intact.
        val recoverAudio = !samsung || (!journal.initialized && (legacyMaintainArmed || legacyInstallation))
        if (recoverAudio &&
            audio.getOrNull() == ForceUse.FORCE_NONE && deviceDefault == ForceUse.FORCE_SYSTEM_ENFORCED) {
            val existing = ShutterChange(ShutterMethod.AUDIO_POLICY, ForceUse.FORCE_SYSTEM_ENFORCED)
            store.save(ShutterJournal(true, existing))
            return readPinned(existing)
        }
        if (!journal.initialized) {
            // Existing releases must remain restorable with the old method. Also
            // preserve an old armed maintenance intent after a device reboot.
            val legacy = legacyMaintainArmed && deviceDefault == ForceUse.FORCE_SYSTEM_ENFORCED &&
                audio.getOrNull() == ForceUse.FORCE_SYSTEM_ENFORCED
            journal = ShutterJournal(true, if (legacy)
                ShutterChange(ShutterMethod.AUDIO_POLICY, ForceUse.FORCE_SYSTEM_ENFORCED) else null)
            store.save(journal)
            journal.change?.let { return readPinned(it) }
        }
        // Preserve the existing default-none/current-none behavior even if a
        // Samsung CSC key happens to exist and disagrees with the audio policy.
        if (deviceDefault == ForceUse.FORCE_NONE && audio.getOrNull() == ForceUse.FORCE_NONE)
            return audioSnapshot(ForceUse.FORCE_NONE, null)
        if (samsung) {
            val csc = runCatching { device.readCsc() }.getOrNull()
            if (csc in listOf("0", "1") && runCatching { device.canWriteCsc() }.getOrDefault(false)) {
                val default = cscDefault()
                // An existing, writable key is required; metadata alone cannot
                // select CSC. A known non-default 0 can be restored explicitly.
                // Default 0 does not hide independently enforced AudioPolicy.
                if (csc == "1" || default == 1 || default == 0 && audio.getOrNull() != ForceUse.FORCE_SYSTEM_ENFORCED)
                    return snapshot(ShutterMethod.GALAXY_CSC, csc!!.toInt(), default ?: 1, false, default)
            }
        }
        return audioSnapshot(audio.getOrThrow(), null)
    }

    fun release(beforeWrite: () -> Unit = {}): ShutterSnapshot {
        val before = inspect()
        // A CSC already at 0 is never rewritten by release. An explicit release
        // may still clear independent AudioPolicy enforcement, without switching
        // an existing pinned CSC transaction to another method.
        if (before.persistent && !before.hasChange && before.rawValue == 0 &&
            runCatching { device.readAudio() }.getOrNull() == ForceUse.FORCE_SYSTEM_ENFORCED)
            return releaseAudio(beforeWrite)
        if (before.enforcement == Enforcement.RELEASED) return before
        check(before.enforcement == Enforcement.ENFORCED) { "Unknown shutter setting" }
        val previous = store.load().change
        val change = previous ?: ShutterChange(before.method,
            if (before.persistent) before.rawValue else checkNotNull(before.restoreValue))
        if (previous == null) store.save(ShutterJournal(true, change))
        // Extra reads and durable saving can outlast a Worker cancellation.
        // Recheck immediately before each write, outside write-failure recovery.
        beforeWrite()
        val failure = runCatching { write(change.method, releasedValue(change.method)) }.exceptionOrNull()
        val after = try { readPinned(change) }
        catch (error: Exception) { throw ShutterVerificationException(error) }
        if (after.enforcement == Enforcement.RELEASED) return finishRelease(after)
        // Only a NEW CSC attempt with a confirmed unchanged value can fall back.
        // An uncertain write, or a pinned prior release, must retain its method.
        if (previous == null && change.method == ShutterMethod.GALAXY_CSC &&
            after.rawValue == change.original && failure !is ShutterWriteUncertainException) {
            store.save(ShutterJournal(true))
            return releaseAudio(beforeWrite)
        }
        throw ShutterVerificationException(failure)
    }

    private fun releaseAudio(beforeWrite: () -> Unit): ShutterSnapshot {
        val before = audioSnapshot(device.readAudio(), null)
        if (before.enforcement == Enforcement.RELEASED) return before
        check(before.enforcement == Enforcement.ENFORCED) { "Unknown audio policy" }
        val change = ShutterChange(ShutterMethod.AUDIO_POLICY, checkNotNull(before.restoreValue))
        store.save(ShutterJournal(true, change))
        beforeWrite()
        val failure = runCatching { device.writeAudio(ForceUse.FORCE_NONE) }.exceptionOrNull()
        val after = try { readPinned(change) }
        catch (error: Exception) { throw ShutterVerificationException(error) }
        if (after.enforcement != Enforcement.RELEASED) throw ShutterVerificationException(failure)
        return finishRelease(after)
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
            beforeWrite()
            val failure = runCatching { write(change.method, target) }.exceptionOrNull()
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
        ShutterMethod.GALAXY_CSC -> {
            val raw = device.readCsc()
            val default = cscDefault()
            snapshot(change.method, when (raw) { "0" -> 0; "1" -> 1; else -> null },
                default ?: change.original, true, default, raw == null || raw in listOf("0", "1"))
        }
    }

    private fun audioSnapshot(value: Int, change: ShutterChange?) =
        snapshot(ShutterMethod.AUDIO_POLICY, value, change?.original ?: deviceDefault ?: value, change != null)

    private fun cscDefault(): Int? = runCatching { device.readCscInfo() }.getOrNull()
        ?.takeIf { it.exists }?.trustedDefault

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
    private fun write(method: ShutterMethod, value: Int?) = when (method) {
        ShutterMethod.AUDIO_POLICY -> device.writeAudio(checkNotNull(value))
        ShutterMethod.GALAXY_CSC -> device.writeCsc(value)
    }
}
