package jp.linkserver.shutter.update
import jp.linkserver.shutter.*
import org.json.JSONArray
import org.json.JSONObject
internal fun encodeUpdate(update: GitHubRelease) = JSONObject().apply {
    put("tag", update.tag); put("notes", update.notes); put("url", update.url)
    put("prerelease", update.prerelease); put("apkName", update.apkName)
    put("apkUrl", update.apkUrl); put("apkSha256", update.apkSha256)
    put("intermediateNotes", JSONArray().apply {
        update.intermediateNotes.forEach { put(JSONObject().put("tag", it.tag).put("notes", it.notes)) }
    })
    put("publishedAt", update.publishedAt)
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
        }, publishedAt = json.optLong("publishedAt", 0))
}
