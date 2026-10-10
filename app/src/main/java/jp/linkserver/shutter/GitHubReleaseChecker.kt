package jp.linkserver.shutter

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

internal data class GitHubRelease(val tag: String, val notes: String, val url: String)

/** Adapted from glyphvisualizer's manual GitHub Releases check. No automatic downloads. */
internal fun checkLatestRelease(): GitHubRelease? {
    val connection = (URI(AppLinks.LATEST_RELEASE_API).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Shutter/${BuildConfig.VERSION_NAME}")
    }
    try {
        if (connection.responseCode == 404) return null
        check(connection.responseCode in 200..299) { "GitHub HTTP ${connection.responseCode}" }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.getString("tag_name").trim()
        check(tag.isNotEmpty()) { "Missing release tag" }
        val url = json.getString("html_url")
        check(url.startsWith("${AppLinks.REPOSITORY}/releases/", ignoreCase = true)) { "Unexpected release URL" }
        return GitHubRelease(tag, if (json.isNull("body")) "" else json.optString("body"), url)
    } finally {
        connection.disconnect()
    }
}
