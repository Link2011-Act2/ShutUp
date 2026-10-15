package jp.linkserver.shutter

import android.content.Context

internal interface CscEmulationValueStore {
    var value: Int
}

/** No device calls: even an accidentally selected AudioPolicy route fails closed. */
internal class EmulatedCscDevice(private val values: CscEmulationValueStore) : ShutterDevice {
    override fun readAudio(): Int = error("AudioPolicy is not part of CSC emulation")
    override fun writeAudio(value: Int): Unit = error("AudioPolicy is not part of CSC emulation")
    override fun readCsc() = values.value.toString()
    override fun readCscInfo() = GalaxyCscSetting(true, values.value, 1, true)
    override fun canWriteCsc() = true
    override fun writeCsc(value: Int?) {
        require(value == 0 || value == 1) { "An emulated CSC key must remain present" }
        values.value = value
    }
}

/** Simulated changes can never become real restoration records after an APK/channel change. */
internal class CscEmulation(values: CscEmulationValueStore, private val journal: ShutterChangeStore) : ShutterChangeStore {
    val device: ShutterDevice = EmulatedCscDevice(values)
    override fun load() = journal.load().copy(initialized = true) // Never infer legacy audio changes.
    override fun save(journal: ShutterJournal) {
        require(journal.change == null || journal.change.method == ShutterMethod.GALAXY_CSC)
        this.journal.save(journal)
    }

    companion object {
        fun create(context: Context, name: String = "developer_csc_emulation"): CscEmulation {
            val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val values = object : CscEmulationValueStore {
                override var value: Int
                    get() = prefs.getInt("value", 1).also { check(it == 0 || it == 1) }
                    set(value) {
                        require(value == 0 || value == 1)
                        check(prefs.edit().putInt("value", value).commit()) { "Could not save emulated CSC value" }
                    }
            }
            return CscEmulation(values, ShutterJournalFile(context, "$name-journal"))
        }
    }
}
