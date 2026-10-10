package jp.linkserver.shutter.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.shutter.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(AppUpdatePreferences(application)) {
        runCatching { checkReleaseUpdate(BuildConfig.VERSION_NAME) }
    }
    val state = repository.state
    private var checkJob: Job? = null
    fun checkForUpdates() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { repository.check() }
    }
    suspend fun checkManually() = repository.check(force = true)
    fun hideBanner() = repository.hideBanner()
    fun ignoreVersion(tag: String) = repository.ignoreVersion(tag)
}
