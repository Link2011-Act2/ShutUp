package jp.linkserver.shutter.update
internal const val CHECK_INTERVAL_MS = 8L * 60 * 60 * 1000
internal fun isUpdateCheckDue(lastTime: Long, lastVersion: String?, currentVersion: String, now: Long): Boolean =
    lastVersion != currentVersion || now < lastTime || now - lastTime >= CHECK_INTERVAL_MS
