package jp.linkserver.shutter.maintain

import jp.linkserver.shutter.ForceUse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class AutoMaintainOfferStage { READY, PENDING, HANDLED }
internal interface AutoMaintainOfferStore {
    fun load(): AutoMaintainOfferStage
    fun save(stage: AutoMaintainOfferStage)
}

/** A durable, one-time offer after a successful manual release; never enables maintenance by itself. */
internal class AutoMaintainOffer(private val store: AutoMaintainOfferStore) {
    private var stage = store.load()
    private val mutablePending = MutableStateFlow(stage == AutoMaintainOfferStage.PENDING)
    val pending = mutablePending.asStateFlow()

    @Synchronized
    fun manualFinished(action: ManualAction, result: Result<Int>, deviceDefault: Int, state: AutoMaintainState) {
        if (stage != AutoMaintainOfferStage.READY || action == ManualAction.RESTORE ||
            result.getOrNull() != ForceUse.FORCE_NONE || deviceDefault == ForceUse.FORCE_NONE || !state.armed) return
        save(if (state.enabled) AutoMaintainOfferStage.HANDLED else AutoMaintainOfferStage.PENDING)
    }

    @Synchronized
    fun manualRequested(action: ManualAction) {
        // A restore/toggle intent invalidates an outstanding offer before waiting for Shizuku.
        if (action != ManualAction.RELEASE) finish()
    }

    @Synchronized
    fun finish() {
        if (stage == AutoMaintainOfferStage.PENDING) save(AutoMaintainOfferStage.HANDLED)
    }

    @Synchronized
    fun respond(maintain: Boolean, enableMaintenance: () -> Unit) {
        if (stage != AutoMaintainOfferStage.PENDING) return
        if (maintain) enableMaintenance()
        finish()
    }

    private fun save(next: AutoMaintainOfferStage) {
        store.save(next)
        stage = next
        mutablePending.value = next == AutoMaintainOfferStage.PENDING
    }
}
