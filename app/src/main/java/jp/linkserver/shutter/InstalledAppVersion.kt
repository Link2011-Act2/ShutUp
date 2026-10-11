package jp.linkserver.shutter

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

internal fun PackageManager.installedPackageInfo(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.archivePackageInfo(path: String, flags: Int): PackageInfo? {
    // Android 9 and 10 only collect archive certificates when the legacy flag is set too.
    val archiveFlags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
        (flags and PackageManager.GET_SIGNING_CERTIFICATES) != 0
    ) {
        flags or PackageManager.GET_SIGNATURES
    } else {
        flags
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(archiveFlags.toLong()))
    } else {
        getPackageArchiveInfo(path, archiveFlags)
    }
}

/** Use installed APK metadata so an inlined BuildConfig value cannot enable developer options. */
internal fun installedAppVersionName(context: Context): String = context.packageManager
    .installedPackageInfo(context.packageName).versionName.orEmpty()
