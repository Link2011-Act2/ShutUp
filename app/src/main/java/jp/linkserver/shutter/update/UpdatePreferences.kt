package jp.linkserver.shutter.update

import android.content.Context
import android.content.SharedPreferences
import jp.linkserver.goldy.*
import jp.linkserver.shutter.installedAppVersionName
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val UPDATE_REPOSITORY = GitHubRepository("Link2011-Act2", "ShutUp")
internal val UPDATE_POLICY = UpdatePolicies.shutUp()

/** Host settings only. Update catalogs, intervals and ignored-tag sets belong to Goldy. */
internal class AppUpdatePreferences(
    context: Context,
    internal val legacy: SharedPreferences = context.applicationContext.getSharedPreferences("github_release_updates", 0),
    internal val settings: SharedPreferences = context.applicationContext.getSharedPreferences("goldy_host_updates", 0),
    private val installedVersion: String = installedAppVersionName(context)
) {
    companion object { private val migrationGate = Mutex() }
    private fun flag(key: String, default: Boolean = false) = runCatching {
        if (settings.contains(key)) settings.getBoolean(key, default) else legacy.getBoolean(key, default)
    }.getOrDefault(default)
    val developerSettings get() = resolveUpdateDeveloperSettings(installedVersion,
        flag("dev_ignore_version_comparison"), flag("dev_ignore_update_interval"))
    val options get() = RuntimeOptions(
        automaticChecksEnabled = flag("automatic_checks_enabled", true),
        notificationsEnabled = flag("notifications_enabled", true),
        developer = DeveloperOptions(developerSettings.ignoreVersionComparison, developerSettings.ignoreInterval))
    val hiddenBannerTag: String? get() = settings.getString("hidden_banner_tag", null)
    fun setIgnoreVersionComparison(value: Boolean) {
        if (developerSettings.available) check(settings.edit().putBoolean("dev_ignore_version_comparison", value).commit())
    }
    fun setIgnoreInterval(value: Boolean) {
        if (developerSettings.available) check(settings.edit().putBoolean("dev_ignore_update_interval", value).commit())
    }
    fun rememberHiddenBanner(tag: String?) {
        check(settings.edit().putString("hidden_banner_tag", tag).commit())
    }

    /** Union-before-marker makes retries after process death/storage failure idempotent. */
    suspend fun migrate(store: UpdateStateStore) = withContext(Dispatchers.IO) {
        migrationGate.withLock {
            if (runCatching { settings.getBoolean("migration_v1", false) }.getOrDefault(false)) return@withLock
            val ignored = runCatching { legacy.getString("dismissed_tag", null) }.getOrNull()
                ?.takeIf { it.isNotBlank() && it.length <= 512 }?.lowercase(Locale.ROOT)
            val previous = store.read()
            store.write(previous.copy(ignoredTags = previous.ignoredTags + listOfNotNull(ignored)))
            val edit = settings.edit()
            for ((key, default) in listOf("automatic_checks_enabled" to true, "notifications_enabled" to true,
                "dev_ignore_version_comparison" to false, "dev_ignore_update_interval" to false)) {
                if (!settings.contains(key)) edit.putBoolean(key, flag(key, default))
            }
            // Old selected-release JSON has no authenticated release/asset IDs or sizes. Keep the
            // original blob for rollback, retain timing for audit, and fetch real metadata once.
            edit.putLong("legacy_last_success_ms", runCatching { legacy.getLong("last_check_ms", 0) }.getOrDefault(0))
            edit.putString("legacy_last_check_version", runCatching { legacy.getString("last_check_version", null) }.getOrNull())
            if (!settings.contains("hidden_banner_tag")) edit.putString("hidden_banner_tag",
                runCatching { legacy.getString("hidden_banner_tag", null) }.getOrNull())
            check(edit.putBoolean("migration_v1", true).commit()) { "Could not commit update settings migration" }
        }
    }
}
