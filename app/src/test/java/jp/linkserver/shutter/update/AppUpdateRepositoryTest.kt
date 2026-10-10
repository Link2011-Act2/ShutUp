package jp.linkserver.shutter.update

import jp.linkserver.shutter.GitHubRelease
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AppUpdateRepositoryTest {
    private val release = GitHubRelease("v2.0.0", "Notes", "https://github.com/Link2011-Act2/ShutUp/releases/tag/v2.0.0")
    private class Store(var cached: GitHubRelease? = null, var due: Boolean = true) : AppUpdateStore {
        var saves = 0
        var ignored: String? = null
        override fun loadUpdate() = cached
        override fun shouldCheck() = due
        override fun saveSuccessfulCheck(update: GitHubRelease?) { cached = update; saves++; due = false }
        override fun isDismissed(tag: String) = tag.equals(ignored, true)
        override fun dismiss(tag: String) { ignored = tag }
    }

    @Test fun cacheAndIntervalAvoidNetworkButManualChecksForceIt() = runBlocking {
        val store = Store(release, false)
        var calls = 0
        val repo = AppUpdateRepository(store) { calls++; Result.success(release) }
        repo.check()
        assertTrue(repo.state.value.showBanner)
        assertEquals(0, calls)
        repo.check(true)
        repo.check()
        assertEquals(1, calls)
        assertEquals(1, store.saves)
    }

    @Test fun failureRetainsCacheAndAllowsRetry() = runBlocking {
        val store = Store(release)
        val repo = AppUpdateRepository(store) { Result.failure(IllegalStateException("offline")) }
        assertTrue(repo.check().isFailure)
        assertTrue(repo.check(true).isFailure)
        assertEquals(release, repo.state.value.notificationUpdate)
        assertEquals(0, store.saves)
        assertTrue(store.due)
    }

    @Test fun bannerDismissalIsTemporaryAndIgnoredVersionsStayAvailableManually() = runBlocking {
        val store = Store(release, false)
        val repo = AppUpdateRepository(store) { Result.success(release) }
        repo.check(); repo.hideBanner(); repo.check()
        assertFalse(repo.state.value.showBanner)
        assertEquals(release, repo.state.value.notificationUpdate)
        val restart = AppUpdateRepository(store) { Result.success(release) }
        restart.check()
        assertTrue(restart.state.value.showBanner)
        restart.ignoreVersion(release.tag)
        val ignoredRestart = AppUpdateRepository(store) { Result.success(release) }
        assertEquals(release, ignoredRestart.check(true).getOrThrow())
        assertNull(ignoredRestart.state.value.notificationUpdate)
        assertEquals(release, ignoredRestart.state.value.availableUpdate)
    }

    @Test fun ignoreSurvivesAnInFlightCheckAndNewVersionReappears() = runBlocking {
        val store = Store(release)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repo = AppUpdateRepository(store) {
            started.complete(Unit); finish.await(); Result.success(release)
        }
        val pending = async { repo.check() }
        started.await(); repo.ignoreVersion(release.tag); finish.complete(Unit); pending.await()
        assertNull(repo.state.value.notificationUpdate)
        val next = release.copy(tag = "v2.1.0")
        val restart = AppUpdateRepository(store) { Result.success(next) }
        restart.check(true)
        assertEquals(next, restart.state.value.notificationUpdate)
        assertTrue(restart.state.value.showBanner)
    }

    @Test fun noUpdateClearsTheCacheAndCancellationDoesNotSave() = runBlocking {
        val store = Store(release)
        val cancelled = AppUpdateRepository(store) { Result.failure(CancellationException()) }
        try { cancelled.check(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(release, store.cached)
        assertEquals(0, store.saves)
        AppUpdateRepository(store) { Result.success(null) }.check()
        assertNull(store.cached)
        assertEquals(1, store.saves)
    }

    @Test fun intervalIncludesVersionChangesAndClockRollback() {
        assertFalse(isUpdateCheckDue(100, "1.0", "1.0", 100 + CHECK_INTERVAL_MS - 1))
        assertTrue(isUpdateCheckDue(100, "1.0", "1.0", 100 + CHECK_INTERVAL_MS))
        assertTrue(isUpdateCheckDue(100, "1.0", "1.1", 101))
        assertTrue(isUpdateCheckDue(100, "1.0", "1.0", 99))
    }
}
