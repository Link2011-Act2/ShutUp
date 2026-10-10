package jp.linkserver.shutter.update

import android.content.Context
import android.content.SharedPreferences
import jp.linkserver.shutter.*
import org.json.JSONArray
import org.json.JSONObject

internal const val CHECK_INTERVAL_MS = 8L * 60 * 60 * 1000
internal fun isUpdateCheckDue(lastTime: Long, lastVersion: String?, currentVersion: String, now: Long): Boolean =
    lastVersion != currentVersion || now < lastTime || now - lastTime >= CHECK_INTERVAL_MS

internal class AppUpdatePreferences(context: Context,
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("github_release_updates", Context.MODE_PRIVATE),
    private val installedVersion: String = BuildConfig.VERSION_NAME
) : AppUpdateStore {
    override fun loadUpdate(): GitHubRelease? = runCatching {
        val saved = prefs.getString("cached_update", null) ?: return null
        decodeUpdate(JSONObject(saved)).takeIf {
            isRepositoryReleaseUrl(it.url) &&
                (it.apkUrl == null || isRepositoryReleaseUrl(it.apkUrl, download = true)) &&
                acceptsRelease(it, installedVersion) &&
                (compareReleaseVersions(it.tag, installedVersion) ?: -1) > 0
        }
    }.getOrNull()

    override fun shouldCheck() = isUpdateCheckDue(prefs.getLong("last_check_ms", 0),
        prefs.getString("last_check_version", null), installedVersion, System.currentTimeMillis())
    override fun saveSuccessfulCheck(update: GitHubRelease?) {
        prefs.edit().apply {
            putLong("last_check_ms", System.currentTimeMillis())
            putString("last_check_version", installedVersion)
            if (update == null) remove("cached_update")
            else putString("cached_update", encodeUpdate(update).toString())
        }.apply()
    }
    override fun isDismissed(tag: String) = tag.equals(prefs.getString("dismissed_tag", null), true)
    override fun dismiss(tag: String) { prefs.edit().putString("dismissed_tag", tag).apply() }
}

internal fun encodeUpdate(update: GitHubRelease) = JSONObject().apply {
    put("tag", update.tag); put("notes", update.notes); put("url", update.url)
    put("prerelease", update.prerelease); put("apkName", update.apkName)
    put("apkUrl", update.apkUrl); put("apkSha256", update.apkSha256)
    put("intermediateNotes", JSONArray().apply {
        update.intermediateNotes.forEach { put(JSONObject().put("tag", it.tag).put("notes", it.notes)) }
    })
}

internal fun decodeUpdate(json: JSONObject): GitHubRelease {
    val tag = json.getString("tag")
    require(tag.isNotBlank())
    val notes = json.optJSONArray("intermediateNotes")
    fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
    return GitHubRelease(tag, json.optString("notes"), json.getString("url"), json.optBoolean("prerelease"),
        optional("apkName"), optional("apkUrl"), optional("apkSha256"), buildList {
            for (index in 0 until (notes?.length() ?: 0)) {
                val note = notes?.optJSONObject(index) ?: continue
                val noteTag = note.optString("tag").takeIf { it.isNotBlank() } ?: continue
                add(ReleaseNote(noteTag, note.optString("notes")))
            }
        })
}
