package jp.linkserver.shutter

import android.content.Context
import android.content.pm.PackageManager

/** Use installed APK metadata so an inlined BuildConfig value cannot enable developer options. */
internal fun installedAppVersionName(context: Context): String = context.packageManager
    .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
