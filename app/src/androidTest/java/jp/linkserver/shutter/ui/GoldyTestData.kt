package jp.linkserver.shutter.ui

import android.content.SharedPreferences
import jp.linkserver.goldy.*
import jp.linkserver.shutter.AppLinks
import java.time.Instant

internal fun testUpdate(tag: String = "v99.0.0-Release", withApk: Boolean = false): UpdateCandidate {
    val assets = if (withApk) listOf(GitHubAsset(10, "ShutUp-v2.0.0-Beta2.apk",
        "${AppLinks.REPOSITORY}/releases/download/$tag/ShutUp-v2.0.0-Beta2.apk", 30_000_000, null)) else emptyList()
    return UpdateCandidate(GitHubRelease(1, tag, notes = "Camera settings and interface improvements.\n設定画面とシャッター音の操作を改善しました。",
        url = "${AppLinks.REPOSITORY}/releases/tag/$tag", prerelease = tag.contains("Beta"),
        publishedAt = Instant.parse("2026-01-02T00:00:00Z"), assets = assets), listOf(
        GitHubRelease(2, "v98.0.0-Release", notes = "Earlier improvements", url = "${AppLinks.REPOSITORY}/releases/tag/v98.0.0-Release")))
}

internal fun restorePreferences(prefs: SharedPreferences, values: Map<String, *>) {
    val edit = prefs.edit().clear()
    values.forEach { (key, value) -> when (value) {
        is String -> edit.putString(key, value)
        is Boolean -> edit.putBoolean(key, value)
        is Int -> edit.putInt(key, value)
        is Long -> edit.putLong(key, value)
        is Float -> edit.putFloat(key, value)
        is Set<*> -> { @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>) }
    } }
    check(edit.commit())
}
