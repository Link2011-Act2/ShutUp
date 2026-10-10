package jp.linkserver.shutter.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.shutter.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val installedVersion = installedAppVersionName(application)
    private val preferences = AppUpdatePreferences(application, installedVersion = installedVersion)
    private val mutableDeveloperSettings = MutableStateFlow(preferences.developerSettings)
    val developerSettings = mutableDeveloperSettings.asStateFlow()
    private val repository = AppUpdateRepository(preferences) {
        runCatching { checkReleaseUpdate(installedVersion, preferences.developerSettings.ignoreVersionComparison) }
    }
    val state = repository.state
    private var checkJob: Job? = null
    fun checkForUpdates(force: Boolean = false) {
        if (!force && checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { repository.check(force) }
    }
    fun setIgnoreVersionComparison(enabled: Boolean) {
        if (!preferences.developerSettings.available) return
        preferences.setIgnoreVersionComparison(enabled)
        mutableDeveloperSettings.value = preferences.developerSettings
        repository.refreshCachedState()
        checkForUpdates(force = true)
    }
    fun setIgnoreInterval(enabled: Boolean) {
        if (!preferences.developerSettings.available) return
        preferences.setIgnoreInterval(enabled)
        mutableDeveloperSettings.value = preferences.developerSettings
        checkForUpdates()
    }
    suspend fun checkManually() = repository.check(force = true)
    fun hideBanner() = repository.hideBanner()
    fun ignoreVersion(tag: String) = repository.ignoreVersion(tag)
}
