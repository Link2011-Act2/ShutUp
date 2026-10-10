package jp.linkserver.shutter

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import jp.linkserver.shutter.update.isIntDevUpdateBuild

internal data class ReleaseNote(val tag: String, val notes: String)
internal data class GitHubRelease(
    val tag: String, val notes: String, val url: String,
    val prerelease: Boolean = false, val apkName: String? = null,
    val apkUrl: String? = null, val apkSha256: String? = null,
    val intermediateNotes: List<ReleaseNote> = emptyList(),
    val publishedAt: Long = 0
)

internal fun isRepositoryReleaseUrl(value: String, download: Boolean = false): Boolean = runCatching {
    val uri = URI(value).normalize()
    uri.scheme.equals("https", true) && uri.host.equals("github.com", true) &&
        uri.userInfo == null && uri.port in listOf(-1, 443) &&
        uri.path.startsWith(URI(AppLinks.REPOSITORY).path +
            if (download) "/releases/download/" else "/releases/")
}.getOrDefault(false)

internal fun acceptsRelease(release: GitHubRelease, installed: String): Boolean {
    val suffix = release.tag.substringAfter('-', "").substringBefore('+').lowercase(java.util.Locale.ROOT)
    val isInternal = suffix.startsWith("intdev") || suffix.startsWith("internal")
    val isBeta = suffix.startsWith("beta")
    val stableSuffix = suffix.isEmpty() || suffix == "stable" || suffix == "release"
    return when (detectReleaseChannel(installed)) {
        "IntDev" -> true
        "Beta" -> !isInternal && (isBeta || (!release.prerelease && stableSuffix))
        else -> !release.prerelease && stableSuffix
    }
}

internal fun selectReleaseUpdate(releases: List<GitHubRelease>, installed: String,
    ignoreVersionComparison: Boolean = false): GitHubRelease? {
    if (ignoreVersionComparison && isIntDevUpdateBuild(installed)) {
        // Test the most recently published release, including same/older or non-version tags.
        return releases.filter { acceptsRelease(it, installed) }.maxByOrNull { it.publishedAt }
            ?.copy(intermediateNotes = emptyList())
    }
    require(compareReleaseVersions(installed, installed) != null) { "Unrecognized installed version" }
    val candidates = releases.filter {
        acceptsRelease(it, installed) && (compareReleaseVersions(it.tag, installed) ?: -1) > 0
    }.sortedWith { a, b -> compareReleaseVersions(b.tag, a.tag) ?: 0 }
    val latest = candidates.firstOrNull() ?: return null
    return latest.copy(intermediateNotes = candidates.drop(1).map { ReleaseNote(it.tag, it.notes) })
}

internal fun parseGitHubReleases(response: String, allowUnversionedTags: Boolean = false): List<GitHubRelease> {
    val array = JSONArray(response)
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            if (json.optBoolean("draft")) continue
            val tag = json.optString("tag_name").trim()
            val url = json.optString("html_url")
            if (tag.isBlank() || (!allowUnversionedTags && compareReleaseVersions(tag, tag) == null) ||
                !isRepositoryReleaseUrl(url)) continue
            val assets = json.optJSONArray("assets")
            val apks = buildList {
                for (assetIndex in 0 until (assets?.length() ?: 0)) {
                    val asset = assets?.optJSONObject(assetIndex) ?: continue
                    if (asset.optString("name").endsWith(".apk", true) &&
                        isRepositoryReleaseUrl(asset.optString("browser_download_url"), download = true)) add(asset)
                }
            }
            val apk = apks.maxByOrNull { if (it.optString("name").contains("shutup", true)) 1 else 0 }
            val digest = apk?.optString("digest")?.takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }
                ?.substringAfter(':')
            add(GitHubRelease(tag, if (json.isNull("body")) "" else json.optString("body"), url,
                json.optBoolean("prerelease"), apk?.optString("name"),
                apk?.optString("browser_download_url"), digest,
                publishedAt = runCatching { Instant.parse(json.getString("published_at")).toEpochMilli() }
                    .getOrDefault(0)))
        }
    }
}

internal fun checkReleaseUpdate(installed: String, ignoreVersionComparison: Boolean = false): GitHubRelease? {
    val bypassComparison = ignoreVersionComparison && isIntDevUpdateBuild(installed)
    val connection = (URI(AppLinks.RELEASES_API).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "ShutUp/$installed")
    }
    try {
        check(connection.responseCode in 200..299) { "GitHub HTTP ${connection.responseCode}" }
        return selectReleaseUpdate(parseGitHubReleases(
            connection.inputStream.bufferedReader().use { it.readText() }, bypassComparison),
            installed, bypassComparison)
    } finally { connection.disconnect() }
}
