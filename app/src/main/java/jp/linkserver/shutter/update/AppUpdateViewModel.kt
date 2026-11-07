package jp.linkserver.shutter.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.shutter.installedAppVersionName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Goldy is the only update catalog/check/selection/notification state owner. */
internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppUpdatePreferences(application)
    private val store = UpdateStateStores.preferences(application, UPDATE_REPOSITORY)
    private val engine = Goldy.create(application, GoldyConfig(UPDATE_REPOSITORY, UPDATE_POLICY,
        initialOptions = preferences.options, userAgent = "ShutUp/${installedAppVersionName(application)}"), store)
    private val initialized = MutableStateFlow(false)
    private val failure = MutableStateFlow<CheckOutcome.Failure?>(null)
    private val initializationGate = Mutex()
    private val mutableDeveloperSettings = MutableStateFlow(preferences.developerSettings)
    val developerSettings = mutableDeveloperSettings.asStateFlow()
    val state = combine(engine.state, initialized, failure) { current, ready, error ->
        if (!ready) GoldyState(lastOutcome = error) else if (error != null) current.copy(lastOutcome = error) else current
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GoldyState())

    init { command {} }

    private suspend fun initialize(): Boolean = initializationGate.withLock {
        if (initialized.value) return@withLock true
        try {
            preferences.migrate(store)
            engine.updateOptions(preferences.options)
            engine.restoreCachedState()
            if (preferences.hiddenBannerTag?.equals(engine.state.value.notificationUpdate?.release?.tag, true) == true)
                engine.hideBanner()
            mutableDeveloperSettings.value = preferences.developerSettings
            failure.value = null
            initialized.value = true
            true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failure.value = CheckOutcome.Failure(GoldyError.Storage); false }
    }
    private fun command(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { if (initialize()) { failure.value = null; action() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure.value = CheckOutcome.Failure(GoldyError.Storage) }
        }
    }
    fun checkForUpdates(force: Boolean = false) = command {
        engine.check(if (force) CheckTrigger.Manual else CheckTrigger.Automatic)
    }
    suspend fun checkManually(): CheckOutcome = try {
        if (!initialize()) CheckOutcome.Failure(GoldyError.Storage) else {
            failure.value = null
            engine.check(CheckTrigger.Manual)
        }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { CheckOutcome.Failure(GoldyError.Storage).also { failure.value = it } }

    fun setIgnoreVersionComparison(enabled: Boolean) = command {
        withContext(Dispatchers.IO) { preferences.setIgnoreVersionComparison(enabled) }
        mutableDeveloperSettings.value = preferences.developerSettings
        engine.updateOptions(preferences.options)
        engine.check(CheckTrigger.Manual)
    }
    fun setIgnoreInterval(enabled: Boolean) = command {
        withContext(Dispatchers.IO) { preferences.setIgnoreInterval(enabled) }
        mutableDeveloperSettings.value = preferences.developerSettings
        engine.updateOptions(preferences.options)
        engine.check(CheckTrigger.Automatic)
    }
    fun hideBanner() = command {
        val tag = engine.state.value.notificationUpdate?.release?.tag
        engine.hideBanner()
        withContext(Dispatchers.IO) { preferences.rememberHiddenBanner(tag) }
    }
    fun ignoreVersion(tag: String) = command { engine.ignoreVersion(tag) }
}
