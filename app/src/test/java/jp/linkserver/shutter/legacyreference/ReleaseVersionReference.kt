package jp.linkserver.shutter

private data class ReleaseVersion(val numbers: List<Long>, val suffix: List<String>)

private fun parseReleaseVersion(value: String): ReleaseVersion? {
    val match = Regex(
        """^v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z._-]+))?(?:\+[0-9A-Za-z.-]+)?$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(value.trim()) ?: return null
    val parts = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
    val suffix = match.groupValues[2].lowercase(java.util.Locale.ROOT)
        .takeUnless { it == "release" || it == "stable" }.orEmpty()
    val tokens = suffix.replace(Regex("([a-z])(?=[0-9])"), "$1.")
        .split(Regex("[._-]")).filter { it.isNotEmpty() }
    return ReleaseVersion(parts, tokens)
}

/** Null means the release tag cannot safely be compared to the installed version. */
internal fun compareReleaseVersions(remoteTag: String, installedVersion: String): Int? {
    val remote = parseReleaseVersion(remoteTag) ?: return null
    val installed = parseReleaseVersion(installedVersion) ?: return null
    for (index in 0 until maxOf(remote.numbers.size, installed.numbers.size)) {
        val result = remote.numbers.getOrElse(index) { 0 }.compareTo(installed.numbers.getOrElse(index) { 0 })
        if (result != 0) return result
    }
    if (remote.suffix.isEmpty()) return if (installed.suffix.isEmpty()) 0 else 1
    if (installed.suffix.isEmpty()) return -1
    fun rank(suffix: List<String>) = when (suffix.first()) {
        "intdev", "internal" -> 0
        "alpha" -> 1
        "beta" -> 2
        "rc" -> 3
        else -> 1
    }
    val rankOrder = rank(remote.suffix).compareTo(rank(installed.suffix))
    if (rankOrder != 0) return rankOrder
    for (index in 0 until minOf(remote.suffix.size, installed.suffix.size)) {
        val a = remote.suffix[index]
        val b = installed.suffix[index]
        val numberA = a.toLongOrNull()
        val numberB = b.toLongOrNull()
        val result = when {
            numberA != null && numberB != null -> numberA.compareTo(numberB)
            numberA != null -> -1
            numberB != null -> 1
            else -> a.compareTo(b)
        }
        if (result != 0) return result
    }
    return remote.suffix.size.compareTo(installed.suffix.size)
}
