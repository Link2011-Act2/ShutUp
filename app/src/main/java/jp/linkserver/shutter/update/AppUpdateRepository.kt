package jp.linkserver.shutter.update

import jp.linkserver.shutter.GitHubRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Adapted from NITTC Scheduler's cached, dismissible update notifications. */
internal data class AppUpdateState(
    val availableUpdate: GitHubRelease? = null,
    val notificationUpdate: GitHubRelease? = null,
    val showBanner: Boolean = false
)

internal interface AppUpdateStore {
    fun loadUpdate(): GitHubRelease?
    fun shouldCheck(): Boolean
    fun saveSuccessfulCheck(update: GitHubRelease?)
    fun isDismissed(tag: String): Boolean
    fun dismiss(tag: String)
}

internal class AppUpdateRepository(
    private val store: AppUpdateStore,
    private val checkRelease: suspend () -> Result<GitHubRelease?>
) {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var hiddenBannerTag: String? = null

    suspend fun check(force: Boolean = false): Result<GitHubRelease?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            publish(store.loadUpdate())
            if (!force && !store.shouldCheck()) return@withLock Result.success(state.value.availableUpdate)
            val result = checkRelease()
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.map { update ->
                store.saveSuccessfulCheck(update)
                store.loadUpdate().also(::publish)
            }
        }
    }

    fun refreshCachedState() = publish(store.loadUpdate())

    @Synchronized fun hideBanner() {
        hiddenBannerTag = state.value.notificationUpdate?.tag
        publish(state.value.availableUpdate)
    }

    @Synchronized fun ignoreVersion(tag: String) {
        store.dismiss(tag)
        publish(state.value.availableUpdate)
    }

    @Synchronized private fun publish(update: GitHubRelease?) {
        val notification = update?.takeUnless { store.isDismissed(it.tag) }
        mutableState.value = AppUpdateState(update, notification,
            notification != null && !notification.tag.equals(hiddenBannerTag, true))
    }
}
